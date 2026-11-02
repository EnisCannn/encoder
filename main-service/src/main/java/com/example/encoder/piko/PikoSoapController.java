package com.example.encoder.piko;

import lombok.extern.slf4j.Slf4j;
import com.example.encoder.entity.JobStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Piko encoder'in SOAP arayuzunun taklidi. CMS (turksat-tvhy) Piko'ya ne
 * gonderiyorsa aynisini buraya gonderir; adres, metot adlari ve cevap bicimi
 * Piko'yla ayni oldugu icin CMS'te kod degismeden encoder olarak tanimlanabiliyoruz.
 *
 * Cevaplar CMS'in PikoApiClient / PikoEncoderServiceImpl'deki okuma bicimine gore
 * yaziliyor; oradaki parse etme basit metin aramasi, bu yuzden ayrintilar onemli:
 * - Deger <return> etiketinden ham olarak okunuyor, XML kacisi geri cevrilmiyor.
 * - Durum cevabinda "error", "failed" veya "aborted" gecerse is hatali sayiliyor.
 * - Dolu bir "completed" alani isin bittigi anlamina geliyor.
 */
@Slf4j
@RestController
public class PikoSoapController {

    private static final String SOAP_ENV_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final DateTimeFormatter COMPLETED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final CmsJobService cmsJobService;
    private final String username;
    private final String password;

    public PikoSoapController(CmsJobService cmsJobService,
                              @Value("${encoder.cms.username}") String username,
                              @Value("${encoder.cms.password}") String password) {
        this.cmsJobService = cmsJobService;
        this.username = username;
        this.password = password;
    }

    /** CMS'in kullandigi adres: /pencadmin/index.php?r=remote/output&ws=1 */
    @PostMapping(value = "/pencadmin/index.php", produces = "text/xml;charset=UTF-8")
    public ResponseEntity<String> handle(@RequestBody String body) {
        String method;
        Map<String, String> params;
        try {
            Element call = parseCall(body);
            method = call.getLocalName();
            params = childValues(call);
        } catch (Exception e) {
            return fault("Gecersiz SOAP istegi: " + e.getMessage());
        }

        log.info("[PikoSoap] {} {}", method, loggable(params));

        return switch (method) {
            case "login" -> ok(method, login(params));
            case "VODGenerateJobNew" -> ok(method, generateJob(params));
            case "VODJobStatus" -> ok(method, jobStatus(params));
            case "VODJobStop", "VODJobDelete" -> ok(method, stopJob(params));
            default -> fault("Desteklenmeyen metot: " + method);
        };
    }

    /**
     * Basarili giriste bir oturum kimligi, hatali giriste bos deger doner; CMS bos
     * degeri "login failed" diye hataya ceviriyor. CMS sonraki cagrilarda bu kimligi
     * gondermiyor (Piko'da da oyle), yani asil koruma ag seviyesinde olmali.
     */
    private String login(Map<String, String> params) {
        boolean valid = username.equals(params.get("name")) && password.equals(params.get("password"));
        return valid ? UUID.randomUUID().toString() : "";
    }

    /** {"result":"57"} ya da hata aciklamasi; CMS sayi olmayan sonucu hata sayiyor. */
    private String generateJob(Map<String, String> params) {
        try {
            CmsJob job = cmsJobService.create(
                    params.get("master_preset_name"),
                    params.get("input_file_disk"),
                    params.get("output_file_disk"));
            return json("result", String.valueOf(job.getId()));
        } catch (RuntimeException e) {
            return json("result", "Is acilamadi: " + e.getMessage());
        }
    }

    private String jobStatus(Map<String, String> params) {
        CmsJobStatus status;
        try {
            status = cmsJobService.status(parseJobId(params));
        } catch (RuntimeException e) {
            return json("status", "error", "message", e.getMessage());
        }

        String progress = String.valueOf(status.progress());
        return switch (status.status()) {
            case COMPLETED -> json("status", "done", "progress", progress,
                    "completed", COMPLETED_FORMAT.format(status.completedAt()));
            case FAILED -> json("status", "failed", "progress", progress, "completed", "",
                    "message", status.errorMessage());
            case CANCELLED -> json("status", "aborted", "progress", progress, "completed", "");
            // Bu kollarda "error/failed/aborted" kelimeleri gecmemeli, yoksa CMS isi hatali sanar
            default -> json("status", status.status() == JobStatus.PENDING ? "waiting" : "running",
                    "progress", progress, "completed", "");
        };
    }

    private String stopJob(Map<String, String> params) {
        try {
            return json("result", "ok", "cancelled", String.valueOf(cmsJobService.cancel(parseJobId(params))));
        } catch (RuntimeException e) {
            return json("result", e.getMessage());
        }
    }

    private static Integer parseJobId(Map<String, String> params) {
        try {
            return Integer.valueOf(params.getOrDefault("jobid", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Gecersiz jobid: " + params.get("jobid"));
        }
    }

    /** Body icindeki ilk eleman cagrilan metot, onun cocuklari parametreler. */
    private static Element parseCall(String body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disaridan gelen XML: DOCTYPE/harici varlik kapali (XXE saldirisina karsi)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        Document document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        NodeList bodies = document.getElementsByTagNameNS(SOAP_ENV_NS, "Body");
        if (bodies.getLength() == 0) {
            throw new IllegalArgumentException("SOAP Body yok");
        }
        Element call = firstElementChild(bodies.item(0));
        if (call == null) {
            throw new IllegalArgumentException("Body bos");
        }
        return call;
    }

    private static Map<String, String> childValues(Element call) {
        Map<String, String> values = new HashMap<>();
        for (Node node = call.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String name = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
                values.put(name, node.getTextContent().trim());
            }
        }
        return values;
    }

    private static Element firstElementChild(Node parent) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private static ResponseEntity<String> ok(String method, String value) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"" + SOAP_ENV_NS + "\""
                + " xmlns:ns1=\"urn:RemoteControllerwsdl\">"
                + "<SOAP-ENV:Body><ns1:" + method + "Response>"
                + "<return>" + xmlText(value) + "</return>"
                + "</ns1:" + method + "Response></SOAP-ENV:Body></SOAP-ENV:Envelope>";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/xml;charset=UTF-8")).body(xml);
    }

    /** CMS 200 disindaki her cevabi hata olarak goruyor; SOAP'ta hata kodu 500. */
    private static ResponseEntity<String> fault(String message) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"" + SOAP_ENV_NS + "\"><SOAP-ENV:Body>"
                + "<SOAP-ENV:Fault><faultcode>SOAP-ENV:Client</faultcode>"
                + "<faultstring>" + xmlText(message) + "</faultstring></SOAP-ENV:Fault>"
                + "</SOAP-ENV:Body></SOAP-ENV:Envelope>";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.parseMediaType("text/xml;charset=UTF-8")).body(xml);
    }

    /**
     * Duz JSON nesnesi. CMS degerleri tirnaktan tirnaga okudugu icin degerin
     * icindeki tirnak ve ters bolu tek tirnak/duz boluye cevriliyor; standart
     * JSON kacisi (\") CMS'in okumasini bozardi.
     */
    private static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keyValues[i]).append("\":\"").append(clean(keyValues[i + 1])).append('"');
        }
        return sb.append('}').toString();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('"', '\'').replace('\\', '/').replace('\n', ' ').replace('\r', ' ');
    }

    /** Yalnizca XML'i bozan karakterler kaciriliyor; tirnaklar CMS ham okudugu icin oldugu gibi kaliyor. */
    private static String xmlText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Log'a parola yazilmasin. */
    private static Map<String, String> loggable(Map<String, String> params) {
        Map<String, String> copy = new HashMap<>(params);
        copy.computeIfPresent("password", (k, v) -> "***");
        return copy;
    }
}
