package saket.consumer.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import saket.consumer.domain.EventDTO;
import saket.consumer.domain.TransactionLog;
import saket.consumer.repositories.TransactionLogRepository;
import saket.consumer.services.state_dependent_behavior.ITransactionBehavior;

@Slf4j
@Component
public class TransactionStrategy implements ITypeStrategy {

    private final ObjectMapper jsonReader;
    private final TransactionLogRepository transactionLogRepository;
    private final ITransactionBehavior transactionBehavior;

    public TransactionStrategy(
        ObjectMapper jsonReader,
        TransactionLogRepository transactionLogRepository,
        ITransactionBehavior transactionBehavior
    ) {
        this.jsonReader = jsonReader;
        this.transactionLogRepository = transactionLogRepository;
        this.transactionBehavior = transactionBehavior;
    }

    @Override
    public String getTopicType() {
        return "saket.wallet";
    }

    @Transactional
    @Override
    public void handle(EventDTO event) {
        TransactionLog payload;
        try {
            payload = jsonReader.treeToValue(event.payload(), TransactionLog.class);
        } catch (JsonProcessingException e) {
            log.error("JSON payload from EventDTO was malformed in TransactionStrategy.");
            return;
        }
        TransactionLog completeTransactionLog = transactionBehavior.onTransactionEvent(payload);
        transactionLogRepository.save(completeTransactionLog);
    }
}
