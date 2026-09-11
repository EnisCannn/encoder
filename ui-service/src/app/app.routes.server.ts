import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    // Prerender KULLANILMIYOR. Sayfalar guard arkasinda oldugu icin prerender
    // sirasinda guard sunucuda calisip her korumali rotayi "/login'e git"
    // diyen statik bir HTML'e ceviriyordu; adres cubuguna /jobs yazan
    // kullanici Angular acilmadan login'e sekiyordu. Panelde SEO ihtiyaci da
    // yok, veri zaten token arkasinda.
    path: '**',
    renderMode: RenderMode.Client,
  },
];
