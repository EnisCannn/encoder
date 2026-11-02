/**
 * Tum API cagrilari artik gateway uzerinden gidiyor. Servisler dogrudan
 * 8081'e degil buraya bakiyor; boylece adres tek yerden degisiyor.
 * (8080 EDB'nin Apache'sinde, 8090 ise yerelde calisan CMS'te oldugu icin
 * gateway disariya 9090'dan aciliyor.)
 */
export const environment = {
  apiBaseUrl: 'http://localhost:9090',
};
