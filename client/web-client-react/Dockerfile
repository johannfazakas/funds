FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY build/dist/js/productionExecutable/ /usr/share/nginx/html/
COPY docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
