import { MatSelect } from '@angular/material/select';
import { Preset } from './components/preset/preset';

// Secim kutularindaki (mat-select) arama alani icin ortak yardimcilar.
// Sablon sayisi, ozellikle kalibrasyon taramasinin urettikleriyle, yuzleri
// buldugu icin uzun listeden elle secim yapmak zorlasiyordu.

/** Turkce harf duyarsiz; bosluklu yazilan her kelime metinde gecmeli. */
export function matchesSearch(haystack: string, query: string): boolean {
  const q = query.trim().toLocaleLowerCase('tr');
  if (!q) return true;
  const text = haystack.toLocaleLowerCase('tr');
  return q.split(/\s+/).every((word) => text.includes(word));
}

/** Sablonun aranabilir metni: "1080p", "h264", "5000" ya da "kalibrasyon" yazarak da bulunur. */
export function presetSearchText(p: Preset): string {
  return [
    p.name,
    p.format,
    `${p.width}x${p.height}`,
    `${p.height}p`,
    p.videoCodec,
    `${p.videoBitrate} kbps`,
    p.calibration ? 'kalibrasyon' : '',
  ].join(' ');
}

/**
 * Arama kutusunun tuslari. Yazilan harfler ve bosluk mat-select'e gitmemeli
 * (bosluk secenegi secer, harfler ilk harfe atlar). Ok tuslari, Enter, Esc ve
 * Tab ise listeye elle iletiliyor: mat-select klavyeyi panelde degil kendi
 * kutusunda dinledigi icin overlay'deki inputtan gelen tuslar ona ulasmiyor.
 */
export function stopSelectKeys(event: KeyboardEvent, select: MatSelect): void {
  event.stopPropagation();
  const navigation = ['ArrowDown', 'ArrowUp', 'Enter', 'Escape', 'Tab'];
  if (navigation.includes(event.key)) select._handleKeydown(event);
}

/** Liste acilinca arama kutusuna odaklanir. */
export function focusSearch(input: HTMLInputElement): void {
  setTimeout(() => input.focus());
}
