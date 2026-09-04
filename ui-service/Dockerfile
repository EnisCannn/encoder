FROM nginx:alpine
COPY dist/ui-service/browser/ /usr/share/nginx/html/
RUN mv /usr/share/nginx/html/index.csr.html /usr/share/nginx/html/index.html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
