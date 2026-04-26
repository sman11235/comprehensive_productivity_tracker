FROM python:3.12-slim

WORKDIR /app

COPY python-producers/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY python-producers/ ./

RUN sed -i 's/\r$//' /app/run_container.sh \
    && chmod +x /app/run_container.sh

CMD ["sh", "/app/run_container.sh"]
