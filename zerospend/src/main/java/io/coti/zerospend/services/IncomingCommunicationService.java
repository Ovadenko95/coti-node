package io.coti.zerospend.services;

import io.coti.common.communication.interfaces.IPropagationSubscriber;
import io.coti.common.communication.interfaces.IReceiver;
import io.coti.common.data.AddressData;
import io.coti.common.data.DspVote;
import io.coti.common.data.TransactionData;
import io.coti.common.data.ZeroSpendTransactionRequest;
import io.coti.zerospend.services.interfaces.IZeroSpendTrxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class IncomingCommunicationService {
    @Autowired
    private IReceiver zeroMQTransactionReceiver;
    @Autowired
    private IZeroSpendTrxService zeroSpendTrxService;
    @Autowired
    private TransactionService transactionService;

    @Autowired
    private IPropagationSubscriber propagationSubscriber;
    @Autowired
    private AddressService addressService;
    @Autowired
    private DspVoteService dspVoteService;

    private Function<Object, String> newZsTransactionRequest = zeroSpendRequestFromDSP -> {
        if (zeroSpendRequestFromDSP != null) {
            if (zeroSpendRequestFromDSP instanceof ZeroSpendTransactionRequest) {
                zeroSpendTrxService.receiveZeroSpendTransaction(((ZeroSpendTransactionRequest) zeroSpendRequestFromDSP).getTransactionData());
                return "GOOD!";
            } else {
                return "BAD!";
            }
        } else {
            return "BAD!";
        }
    };

    @PostConstruct
    private void init() {
        initZeroSpendTrxRequestReceiver();
        initPropagationSubscriber();
    }

    private void initPropagationSubscriber() {
        HashMap<String, Consumer<Object>> classNameToSubscriberHandlerMapping = new HashMap<>();
        classNameToSubscriberHandlerMapping.put(TransactionData.class.getName() + "DSP Nodes", transactionData ->
                transactionService.handlePropagatedTransactionFromDspNode((TransactionData) transactionData));
        classNameToSubscriberHandlerMapping.put(AddressData.class.getName() + "DSP Nodes", data ->
                addressService.handlePropagatedAddress((AddressData) data));
        propagationSubscriber.init(classNameToSubscriberHandlerMapping);
    }

    private void initZeroSpendTrxRequestReceiver() {
        HashMap<String, Function<Object, String>> classNameToReceiverHandlerMapping = new HashMap<>();
        classNameToReceiverHandlerMapping.put(DspVote.class.getName(), dspVote -> dspVoteService.receiveDspVote((DspVote) dspVote));
        classNameToReceiverHandlerMapping.put(ZeroSpendTransactionRequest.class.getName(), newZsTransactionRequest);
        zeroMQTransactionReceiver.init(classNameToReceiverHandlerMapping);
    }
}