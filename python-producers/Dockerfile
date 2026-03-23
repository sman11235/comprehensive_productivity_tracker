FROM python:3.12-slim

WORKDIR /app

COPY python-producers/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY python-producers/ ./

RUN chmod +x /app/run_container.sh

CMD ["/app/run_container.sh"]
