/**
 * Tum API cagrilari artik gateway uzerinden gidiyor. Servisler dogrudan
 * 8081'e degil buraya bakiyor; boylece adres tek yerden degisiyor.
 * (8080 EDB'nin Apache'sinde oldugu icin gateway 8090'da.)
 */
export const environment = {
  apiBaseUrl: 'http://localhost:8090',
};
