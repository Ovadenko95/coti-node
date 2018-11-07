package io.coti.trustscore.data.Events;

import io.coti.basenode.data.Hash;
import io.coti.basenode.data.TransactionData;
import io.coti.trustscore.data.Enums.EventType;
import lombok.Data;

@Data
public class TransactionEventData extends EventData {

    private TransactionData transactionData;

    public TransactionEventData(TransactionData transactionData) {

        this.transactionData = transactionData;
        super.eventType = EventType.TRANSACTION;
    }

    @Override
    public int hashCode() {
        return transactionData.getHash().hashCode();
    }

    @Override
    public Hash getHash() {
        return this.transactionData.getHash();
    }

    @Override
    public void setHash(Hash hash) {
        this.transactionData.setHash(hash);
    }
}