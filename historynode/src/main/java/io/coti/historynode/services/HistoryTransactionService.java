package io.coti.historynode.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.coti.basenode.crypto.CryptoHelper;
import io.coti.basenode.data.BaseTransactionData;
import io.coti.basenode.data.Hash;
import io.coti.basenode.data.ReceiverBaseTransactionData;
import io.coti.basenode.data.TransactionData;
import io.coti.basenode.http.GetEntitiesBulkRequest;
import io.coti.basenode.http.GetEntitiesBulkResponse;
import io.coti.basenode.http.Response;
import io.coti.basenode.http.interfaces.IResponse;
import io.coti.basenode.model.Transactions;
import io.coti.historynode.crypto.GetTransactionsByAddressRequestCrypto;
import io.coti.historynode.crypto.TransactionsRequestCrypto;
import io.coti.historynode.data.AddressTransactionsByAddress;
import io.coti.historynode.data.AddressTransactionsByDate;
import io.coti.historynode.http.GetTransactionsByAddressRequest;
import io.coti.historynode.http.GetTransactionsRequest;
import io.coti.historynode.http.GetTransactionsRequestOld;
import io.coti.historynode.http.HistoryTransactionResponse;
import io.coti.historynode.http.data.HistoryTransactionResponseData;
import io.coti.historynode.http.storageConnector.interaces.IStorageConnector;
import io.coti.historynode.model.AddressTransactionsByAddresses;
import io.coti.historynode.model.AddressTransactionsByDates;
import io.coti.historynode.model.AddressTransactionsByDatesHistories;
import io.coti.historynode.services.interfaces.IHistoryTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.coti.basenode.http.BaseNodeHttpStringConstants.*;

@Slf4j
@Service
public class HistoryTransactionService extends EntityService implements IHistoryTransactionService {

    @Value("${storage.server.address}")
    protected String storageServerAddress;

    @Autowired
    AddressTransactionsByDatesHistories addressTransactionsByDatesHistories;
    @Autowired
    private Transactions transactions;
    @Autowired
    private IStorageConnector storageConnector;

    @Autowired
    private AddressTransactionsByAddresses addressTransactionsByAddresses;
    @Autowired
    private AddressTransactionsByDates addressTransactionsByDates;
    @Autowired
    private GetTransactionsByAddressRequestCrypto getTransactionsByAddressRequestCrypto;
    @Autowired
    private TransactionsRequestCrypto transactionsRequestCrypto;

    private ObjectMapper mapper;

    @PostConstruct
    public void init() {
//        mapper = new ObjectMapper();
        mapper = new ObjectMapper()
                .registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule()); // new module, NOT JSR310Module
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        endpoint = "/transactions";
    }

    @Override
    public ResponseEntity<IResponse> getTransactionsDetails(GetTransactionsRequestOld getTransactionRequest) {
        List<TransactionData> transactionsList = new ArrayList<>();
        List<Hash> hashList = new ArrayList<>();
        GetEntitiesBulkRequest getEntitiesBulkRequest = new GetEntitiesBulkRequest(hashList);

        List<Hash> transactionHashes = getTransactionsHashesByAddresses(getTransactionRequest);
        for (Hash transactionHash : transactionHashes) {
            TransactionData transactionData = transactions.getByHash(transactionHash);
            if (transactionData == null) {
                getEntitiesBulkRequest.getHashes().add(transactionHash);
            } else {
                transactionsList.add(transactionData);
            }
        }

        transactionsList.addAll(getTransactionsDetailsFromStorage(getEntitiesBulkRequest));
        ResponseEntity<IResponse> response = ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(null);
        try {
            response = ResponseEntity
                    .status(HttpStatus.OK)
//                    .body((new GetTransactionsResponse(transactionsList)); //TODO: For initial compilation prior to merge
            .body(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }


    @Override
    public void deleteLocalUnconfirmedTransactions() {
        transactions.forEach(transactionData -> {
            if (!transactionData.isTrustChainConsensus() || !transactionData.getDspConsensusResult().isDspConsensus()) {
                transactions.deleteByHash(transactionData.getHash());
            }
        });
    }

    private List<TransactionData> getTransactionsDetailsFromStorage(GetEntitiesBulkRequest getEntitiesBulkRequest) {
        List<TransactionData> transactionsList = new ArrayList<>();
        if (!getEntitiesBulkRequest.getHashes().isEmpty()) {
            ResponseEntity<IResponse> response
                    = storageConnector.getForObject(storageServerAddress + endpoint, ResponseEntity.class, getEntitiesBulkRequest);
            if (response.getStatusCode() == HttpStatus.OK) {
                //TODO: Convert http message body to transactions, and add to transactions
            }
        }
        return transactionsList;
    }

    private List<Hash> getTransactionsHashesByAddresses(GetTransactionsRequestOld getTransactionRequest) {
        List<Hash> transactionHashes = new ArrayList<>();
        long startDate = getTransactionRequest.getStartingDate() != null ? getTransactionRequest.getStartingDate().getTime() : Long.MIN_VALUE;
        long endDate = getTransactionRequest.getEndingDate() != null ? getTransactionRequest.getEndingDate().getTime() : Long.MAX_VALUE;
        getTransactionRequest.getAddressesHashes().forEach(addressHash ->
                transactionHashes.addAll(
                        addressTransactionsByDatesHistories.getByHash(addressHash)
                                .getTransactionsHistory().subMap(startDate, endDate).values()));
        return transactionHashes;
    }



    public ResponseEntity<IResponse> getTransactionsByAddress(GetTransactionsByAddressRequest getTransactionsByAddressRequest) {
        // Verify signature //TODO: Commented for initial integration testing, uncomment after adding signature in tests
//        if(!getTransactionsByAddressRequestCrypto.verifySignature(getTransactionsByAddressRequest)) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Response(INVALID_SIGNATURE, STATUS_ERROR));
//        }

        Hash address = getTransactionsByAddressRequest.getAddress();
        // Retrieve matching transactions hashes from relevant index
        List<Hash> transactionsHashes = getTransactionsHashesByAddress(address);
        List<Hash> transactionsHashesToRetrieveFromStorage = new ArrayList<>();

        if( transactionsHashes.isEmpty() ) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new Response(TRANSACTIONS_NOT_FOUND, EMPTY_SEARCH_RESULT));
        }

        // Retrieve transactions from local RocksDB, Storage and merge results
        HashMap<Hash, TransactionData> retrievedTransactionsFromLocal = getTransactionsFromLocal(transactionsHashes, transactionsHashesToRetrieveFromStorage);
        HashMap<Hash, TransactionData> retrievedTransactionsFromStorage = getTransactionFromElasticSearch(transactionsHashesToRetrieveFromStorage);
        HashMap<Hash, TransactionData> collectedTransactions = new HashMap<>(retrievedTransactionsFromLocal);
        collectedTransactions.putAll(retrievedTransactionsFromStorage);

        return ResponseEntity.status(HttpStatus.OK).body( new HistoryTransactionResponse(new HistoryTransactionResponseData(collectedTransactions)));
    }

    private HashMap<Hash, TransactionData> getTransactionFromElasticSearch(List<Hash> transactionsHashesToRetrieveFromStorage) {
        HashMap<Hash, TransactionData> retrievedTransactionsFromStorage = new HashMap<>();
        Map<Hash, String> entitiesBulkResponses = null;
        if(!transactionsHashesToRetrieveFromStorage.isEmpty()) {
            entitiesBulkResponses = getTransactionsDataFromElasticSearch(transactionsHashesToRetrieveFromStorage).getBody().getEntitiesBulkResponses();
            if(entitiesBulkResponses == null || entitiesBulkResponses.isEmpty()) {
                log.error("No transactions were retrieved from storage");
                for(Hash transactionHash : transactionsHashesToRetrieveFromStorage) {
                    retrievedTransactionsFromStorage.putIfAbsent(transactionHash, null);
                }
            } else {
                retrievedTransactionsFromStorage =
                        getRetrievedTransactionsFromStorageResponseEntity(transactionsHashesToRetrieveFromStorage, entitiesBulkResponses);
            }
        }
        return retrievedTransactionsFromStorage;
    }

    private HashMap<Hash, TransactionData> getTransactionsFromLocal(List<Hash> transactionsHashes, List<Hash> transactionsHashesToRetrieveFromElasticSearch) {
        HashMap<Hash, TransactionData> retrievedTransactionsFromLocal = new HashMap<>();
        for (Hash transactionsHash : transactionsHashes) {
            TransactionData localTransactionDataByHash = transactions.getByHash(transactionsHash);
            if( localTransactionDataByHash != null) {
                retrievedTransactionsFromLocal.put(transactionsHash, localTransactionDataByHash);
            } else {
                transactionsHashesToRetrieveFromElasticSearch.add(transactionsHash);
            }
        }
        return retrievedTransactionsFromLocal;
    }

    private HashMap<Hash, TransactionData> getRetrievedTransactionsFromStorageResponseEntity(List<Hash> transactionsHashes, Map<Hash, String> entitiesBulkResponses) {
        HashMap<Hash, TransactionData> retrievedTransactions = new HashMap<>();
        transactionsHashes.forEach(transactionHash -> {
            String transactionRetrievedAsJson = entitiesBulkResponses.get(transactionHash);
            if(transactionRetrievedAsJson != null ) {
                try {
                    TransactionData transactionData = mapper.readValue(transactionRetrievedAsJson, TransactionData.class);
                    retrievedTransactions.put(transactionHash, transactionData);
                } catch (IOException e) {
                    log.error("Failed to read value for {}", transactionHash);
                    e.printStackTrace();
                    retrievedTransactions.putIfAbsent(transactionHash, null);
                }
            } else {
                retrievedTransactions.putIfAbsent(transactionHash, null);
                log.error("Failed to retrieve value for {}", transactionHash);
            }
        });
        return retrievedTransactions;
//        return ResponseEntity.status(HttpStatus.OK).body( new HistoryTransactionResponse(new HistoryTransactionResponseData(retrievedTransactions)));
    }

    private ResponseEntity<GetEntitiesBulkResponse> getTransactionsDataFromElasticSearch(List<Hash> transactionsHashes) {
        // Retrieve transactions from storage
        GetEntitiesBulkRequest getEntitiesBulkRequest = new GetEntitiesBulkRequest(transactionsHashes);
        RestTemplate restTemplate = new RestTemplate();
        endpoint = "/transactions";
        return restTemplate.postForEntity(storageServerAddress + endpoint,  getEntitiesBulkRequest,   GetEntitiesBulkResponse.class);
    }


    public ResponseEntity<IResponse> getTransactions(GetTransactionsRequest getTransactionsRequest) {
        // Verify signature //TODO: Commented for initial integration testing, uncomment after adding signature in tests
//        if(!transactionsRequestCrypto.verifySignature(getTransactionsRequest)) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Response(INVALID_SIGNATURE, STATUS_ERROR));
//        }

        List<Hash> transactionsHashes = getTransactionsHashesByGetTransactionsRequest(getTransactionsRequest);

        List<Hash> transactionsHashesToRetrieveFromStorage = new ArrayList<>();

        if( transactionsHashes.isEmpty() ) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new Response(TRANSACTIONS_NOT_FOUND, EMPTY_SEARCH_RESULT));
        }

        // Retrieve transactions from local RocksDB, Storage and merge results
        HashMap<Hash, TransactionData> retrievedTransactionsFromRocksDB = getTransactionsFromLocal(transactionsHashes, transactionsHashesToRetrieveFromStorage);
        HashMap<Hash, TransactionData> retrievedTransactionsFromElasticSearch = getTransactionFromElasticSearch(transactionsHashesToRetrieveFromStorage);
        HashMap<Hash, TransactionData> collectedTransactions = new HashMap<>(retrievedTransactionsFromRocksDB);
        collectedTransactions.putAll(retrievedTransactionsFromElasticSearch);

        return ResponseEntity.status(HttpStatus.OK).body( new HistoryTransactionResponse(new HistoryTransactionResponseData(collectedTransactions)));
    }

    protected List<Hash> getTransactionsHashesByGetTransactionsRequest(GetTransactionsRequest getTransactionsRequest) {
        if( getTransactionsRequest.getStartDate()==null || getTransactionsRequest.getEndDate()==null ) {
            return getTransactionsHashesByAddress(getTransactionsRequest.getTransactionAddress());
        }

        if( getTransactionsRequest.getTransactionAddress()==null ) {
            return getTransactionsHashesByDates(getTransactionsRequest.getStartDate(), getTransactionsRequest.getEndDate());
        }

        return getTransactionsHashesByAddressAndDates(getTransactionsRequest.getTransactionAddress(), getTransactionsRequest.getStartDate(), getTransactionsRequest.getEndDate());
    }



    private List<Hash> getTransactionsHashesByDates(Instant startDate, Instant endDate) {
        if(startDate==null || endDate==null || startDate.isAfter(endDate)) {
            return new ArrayList<>();
        }
        LocalDate localStartDate = getLocalDateByInstant(startDate);
        LocalDate localEndDate = getLocalDateByInstant(endDate);
        List<LocalDate> localDatesBetween = getLocalDatesBetween(localStartDate, localEndDate);

        List<Hash> collectedTransactionsHashes = localDatesBetween.stream().map(localDate -> addressTransactionsByDates.getByHash(getHashByLocalDate(localDate)))
                .flatMap(transactionsByDate -> transactionsByDate.getTransactionsAddresses().stream())
                .distinct() //TODO: Redundant?
                .collect(Collectors.toList());
        return collectedTransactionsHashes;
    }

    private Hash getHashByLocalDate(LocalDate localDate) {
        return CryptoHelper.cryptoHash(localDate.atStartOfDay().toString().getBytes());
    }

    private LocalDate getLocalDateByInstant(Instant date) {
        LocalDateTime ldt = LocalDateTime.ofInstant(date, ZoneOffset.UTC);
        return LocalDate.of(ldt.getYear(), ldt.getMonth(),ldt.getDayOfMonth());
    }

    public static List<LocalDate> getLocalDatesBetween(LocalDate startDate, LocalDate endDate) {
        long numOfDaysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        return IntStream.iterate(0, i -> i + 1)
                .limit(numOfDaysBetween)
                .mapToObj(i -> startDate.plusDays(i))
                .collect(Collectors.toList());
    }

    private List<Hash> getTransactionsHashesByAddress(Hash address) {
        AddressTransactionsByAddress addressTransactionsByAddress = addressTransactionsByAddresses.getByHash(address);
        if(addressTransactionsByAddress==null) {
            return new ArrayList<>();
        }
        SortedMap<LocalDate, HashSet<Hash>> transactionHashesByDates = addressTransactionsByAddress.getTransactionHashesByDates();
        return transactionHashesByDates.keySet().stream().flatMap(key -> transactionHashesByDates.get(key).stream()).collect(Collectors.toList());
    }

    private List<Hash> getTransactionsHashesByAddressAndDates(Hash address, Instant startDate, Instant endDate) {
        AddressTransactionsByAddress addressTransactionsByAddress = addressTransactionsByAddresses.getByHash(address);
        if(addressTransactionsByAddress==null) {
            return new ArrayList<>();
        }
        SortedMap<LocalDate, HashSet<Hash>> transactionHashesByDates = addressTransactionsByAddress.getTransactionHashesByDates();
//        List<LocalDate> localDatesBetween = getLocalDatesBetween(getLocalDateByInstant(startDate),getLocalDateByInstant(endDate));

        SortedMap<LocalDate, HashSet<Hash>> transactionsHashesByDates =
                transactionHashesByDates.subMap(getLocalDateByInstant(startDate), getLocalDateByInstant(endDate).plusDays(1));
        return transactionsHashesByDates.keySet().stream().flatMap(key -> transactionHashesByDates.get(key).stream()).collect(Collectors.toList());
    }


    public void addToHistoryTransactionIndexes(TransactionData transactionData) {
        Instant attachmentTime = transactionData.getAttachmentTime();
        Hash hashByDate = calculateHashByAttachmentTime(attachmentTime);
        LocalDate attachmentLocalDate = calculateInstantLocalDate(attachmentTime);
        HashSet<Hash> relatedAddressHashes = getRelatedAddresses(transactionData);

        AddressTransactionsByDate transactionsByDateHash = addressTransactionsByDates.getByHash(hashByDate);
        if(transactionsByDateHash== null) {
            HashSet<Hash> addresses = new HashSet<>();
            addresses.add(transactionData.getHash());
            addressTransactionsByDates.put(new AddressTransactionsByDate(attachmentTime, addresses));
        } else {
            transactionsByDateHash.getTransactionsAddresses().add(transactionData.getHash());
            addressTransactionsByDates.put(transactionsByDateHash);
        }


        for(Hash transactionAddressHash : relatedAddressHashes) {
            SortedMap<LocalDate, HashSet<Hash>> transactionHashesMap = new TreeMap<>();
            AddressTransactionsByAddress transactionsByAddressHash = addressTransactionsByAddresses.getByHash(transactionAddressHash);
            if(transactionsByAddressHash==null) {
                HashSet<Hash> transactionHashes = new HashSet<>();
                if( !transactionHashes.add(transactionData.getHash()) ) {
                    log.info("{} was already present by address", transactionData);
                }
                transactionHashesMap.put(attachmentLocalDate, transactionHashes);
                addressTransactionsByAddresses.put(new AddressTransactionsByAddress(transactionAddressHash, transactionHashesMap));
            } else {
                HashSet<Hash> transactionsHashesByDate = transactionsByAddressHash.getTransactionHashesByDates().get(hashByDate);
                if(transactionsHashesByDate == null) {
                    HashSet<Hash> transactionsHashes = new HashSet<>();
                    transactionsHashes.add(transactionData.getHash());
                    transactionsByAddressHash.getTransactionHashesByDates().put(attachmentLocalDate, transactionsHashes);
                } else {
                    transactionsHashesByDate.add(transactionData.getHash());
                }
                addressTransactionsByAddresses.put(transactionsByAddressHash);
            }
        }
    }



    public HashSet<Hash> getRelatedAddresses(TransactionData transactionData) {
        HashSet<Hash> hashes = new HashSet<>();
        hashes.add(transactionData.getSenderHash());
        for(BaseTransactionData baseTransactionData : transactionData.getBaseTransactions()) {
            if(baseTransactionData instanceof ReceiverBaseTransactionData) {
                hashes.add(baseTransactionData.getAddressHash());
            }
        }
        return hashes;
    }

    protected Hash calculateHashByAttachmentTime(Instant date) {
        LocalDateTime ldt = LocalDateTime.ofInstant(date, ZoneOffset.UTC);
        LocalDate localDate = LocalDate.of(ldt.getYear(), ldt.getMonth(),ldt.getDayOfMonth());
        return CryptoHelper.cryptoHash(localDate.atStartOfDay().toString().getBytes());
    }

    public LocalDate calculateInstantLocalDate(Instant date) {
        LocalDateTime ldt = LocalDateTime.ofInstant(date, ZoneOffset.UTC);
        return LocalDate.of(ldt.getYear(), ldt.getMonth(),ldt.getDayOfMonth());
    }

}