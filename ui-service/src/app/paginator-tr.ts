import { MatPaginatorIntl } from '@angular/material/paginator';

/**
 * Material sayfalayicisi varsayilan olarak Ingilizce metin basiyor
 * ("Items per page", "1 - 10 of 84"). Arayuzun geri kalani Turkce oldugu icin
 * bu metinleri burada cevirip saglayici olarak veriyoruz.
 */
export function turkishPaginatorIntl(): MatPaginatorIntl {
  const intl = new MatPaginatorIntl();
  intl.itemsPerPageLabel = 'Sayfa başına:';
  intl.nextPageLabel = 'Sonraki sayfa';
  intl.previousPageLabel = 'Önceki sayfa';
  intl.firstPageLabel = 'İlk sayfa';
  intl.lastPageLabel = 'Son sayfa';

  intl.getRangeLabel = (page: number, pageSize: number, length: number) => {
    if (length === 0 || pageSize === 0) {
      return `0 / ${length}`;
    }
    const start = page * pageSize;
    const end = Math.min(start + pageSize, length);
    return `${start + 1} – ${end} / ${length}`;
  };

  return intl;
}
