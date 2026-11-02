package com.example.encoder.piko;

import com.example.encoder.entity.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CMS cevabi ayristirirken metin ariyor: "completed" dolu ise bitti, icinde
 * error/failed/aborted geciyorsa hata. Bu testler o sozlesmeyi koruyor.
 */
@ExtendWith(MockitoExtension.class)
class PikoSoapControllerTest {

    @Mock private CmsJobService cmsJobService;

    private PikoSoapController controller;

    @BeforeEach
    void setUp() {
        controller = new PikoSoapController(cmsJobService, "cms", "gizli");
    }

    // ---------- login ----------

    @Test
    void login_dogruBilgilerleOturumKimligiDoner() {
        ResponseEntity<String> response = call("login", "name", "cms", "password", "gizli");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(returnValue(response)).matches("[0-9a-f-]{36}");
        assertThat(response.getBody()).contains("<ns1:loginResponse>");
    }

    @Test
    void login_yanlisParoladaBosDoner() {
        assertThat(returnValue(call("login", "name", "cms", "password", "yanlis"))).isEmpty();
    }

    // ---------- VODGenerateJobNew ----------

    @Test
    void generate_basariliysaSayiOlarakIsNumarasiDoner() {
        CmsJob job = new CmsJob();
        job.setId(57);
        when(cmsJobService.create("MOVVideo", "/media/in.mov", "/media/out")).thenReturn(job);

        String value = returnValue(call("VODGenerateJobNew", "jobtype", "1", "master_preset_name", "MOVVideo",
                "input_file_disk", "/media/in.mov", "output_file_disk", "/media/out"));

        assertThat(value).isEqualTo("{\"result\":\"57\"}");
    }

    @Test
    void generate_hataliysaSayiOlmayanAciklamaDoner() {
        when(cmsJobService.create(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Giris dosyasi bulunamadi: \"x\""));

        String value = returnValue(call("VODGenerateJobNew", "master_preset_name", "MOVVideo"));

        // Tirnaklar tek tirnaga cevrilir; yoksa CMS sonucu yanlis yerden keser
        assertThat(value).isEqualTo("{\"result\":\"Is acilamadi: Giris dosyasi bulunamadi: 'x'\"}");
    }

    // ---------- VODJobStatus ----------

    @Test
    void status_bitenIsDoluCompletedAlaniylaDoner() {
        when(cmsJobService.status(5)).thenReturn(
                new CmsJobStatus(JobStatus.COMPLETED, 100, null, Instant.parse("2026-11-01T10:00:00Z")));

        String value = returnValue(call("VODJobStatus", "jobid", "5"));

        assertThat(value).startsWith("{\"status\":\"done\",\"progress\":\"100\",\"completed\":\"20");
    }

    @Test
    void status_hataliIsFailedVeMesajDoner() {
        when(cmsJobService.status(5)).thenReturn(new CmsJobStatus(JobStatus.FAILED, 30, "ffmpeg hatasi", null));

        String value = returnValue(call("VODJobStatus", "jobid", "5"));

        assertThat(value).contains("\"status\":\"failed\"", "\"completed\":\"\"", "\"message\":\"ffmpeg hatasi\"");
    }

    @Test
    void status_iptalEdilenIsAbortedDoner() {
        when(cmsJobService.status(5)).thenReturn(new CmsJobStatus(JobStatus.CANCELLED, 10, null, null));

        assertThat(returnValue(call("VODJobStatus", "jobid", "5"))).contains("\"status\":\"aborted\"");
    }

    @Test
    void status_calisanVeBekleyenIsteHataKelimesiGecmez() {
        when(cmsJobService.status(5)).thenReturn(new CmsJobStatus(JobStatus.PROCESSING, 42, null, null));
        when(cmsJobService.status(6)).thenReturn(new CmsJobStatus(JobStatus.PENDING, 0, null, null));

        String running = returnValue(call("VODJobStatus", "jobid", "5"));
        String waiting = returnValue(call("VODJobStatus", "jobid", "6"));

        assertThat(running).isEqualTo("{\"status\":\"running\",\"progress\":\"42\",\"completed\":\"\"}");
        assertThat(waiting).isEqualTo("{\"status\":\"waiting\",\"progress\":\"0\",\"completed\":\"\"}");
        assertThat(running + waiting).doesNotContain("error", "failed", "aborted");
    }

    @Test
    void status_sayiOlmayanIsNumarasindaErrorDoner() {
        String value = returnValue(call("VODJobStatus", "jobid", "abc"));

        assertThat(value).contains("\"status\":\"error\"", "Gecersiz jobid");
        verifyNoInteractions(cmsJobService);
    }

    // ---------- VODJobStop / VODJobDelete ----------

    @Test
    void stopVeDeleteBekleyenProfilleriIptalEder() {
        when(cmsJobService.cancel(5)).thenReturn(3);

        assertThat(returnValue(call("VODJobStop", "jobid", "5")))
                .isEqualTo("{\"result\":\"ok\",\"cancelled\":\"3\"}");
        assertThat(returnValue(call("VODJobDelete", "jobid", "5")))
                .isEqualTo("{\"result\":\"ok\",\"cancelled\":\"3\"}");
    }

    // ---------- hatali istekler ----------

    @Test
    void bilinmeyenMetotSoapFaultIle500Doner() {
        ResponseEntity<String> response = call("Uydurma");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("<SOAP-ENV:Fault>", "Desteklenmeyen metot: Uydurma");
    }

    @Test
    void bozukXmlSoapFaultIle500Doner() {
        ResponseEntity<String> response = controller.handle("bu xml degil");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Gecersiz SOAP istegi");
    }

    @Test
    void doctypeIcerenIstekReddedilir() {
        // XXE: harici varlik ile sunucudaki bir dosyayi cevaba gomme denemesi
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY dosya SYSTEM \"file:///etc/passwd\">]>"
                + envelope("login", "<name>&dosya;</name><password>gizli</password>");

        ResponseEntity<String> response = controller.handle(xxe);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).doesNotContain("root:");
    }

    @Test
    void cevaptakiXmlOzelKarakterleriKacirilir() {
        when(cmsJobService.create(any(), any(), any())).thenThrow(new IllegalArgumentException("a<b & c>d"));

        ResponseEntity<String> response = call("VODGenerateJobNew", "master_preset_name", "x");

        assertThat(response.getBody()).contains("a&lt;b &amp; c&gt;d");
    }

    // ---------- yardimcilar ----------

    /** CMS'in PikoApiClient'inin gonderdigi govdenin aynisi. */
    private ResponseEntity<String> call(String method, String... keyValues) {
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.append('<').append(keyValues[i]).append('>')
                    .append(keyValues[i + 1])
                    .append("</").append(keyValues[i]).append('>');
        }
        return controller.handle(envelope(method, params.toString()));
    }

    private static String envelope(String method, String params) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:urn=\"urn:RemoteControllerwsdl\"><soapenv:Header/><soapenv:Body>"
                + "<urn:" + method + ">" + params + "</urn:" + method + ">"
                + "</soapenv:Body></soapenv:Envelope>";
    }

    /** CMS'in yaptigi gibi <return> etiketinin ham icerigi. */
    private static String returnValue(ResponseEntity<String> response) {
        String body = response.getBody();
        int start = body.indexOf("<return>") + "<return>".length();
        int end = body.indexOf("</return>");
        return body.substring(start, end);
    }
}
