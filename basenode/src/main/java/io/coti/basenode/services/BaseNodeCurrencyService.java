package io.coti.basenode.services;

import io.coti.basenode.crypto.CurrencyRegistrarCrypto;
import io.coti.basenode.crypto.CurrencyTypeRegistrationCrypto;
import io.coti.basenode.crypto.GetUpdatedCurrencyRequestCrypto;
import io.coti.basenode.data.*;
import io.coti.basenode.exceptions.CurrencyInitializationException;
import io.coti.basenode.exceptions.CurrencyNotFoundException;
import io.coti.basenode.http.CustomRequestCallBack;
import io.coti.basenode.http.GetUpdatedCurrencyRequest;
import io.coti.basenode.http.HttpJacksonSerializer;
import io.coti.basenode.model.Currencies;
import io.coti.basenode.services.interfaces.IChunkService;
import io.coti.basenode.services.interfaces.ICurrencyService;
import io.coti.basenode.services.interfaces.INetworkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.FluxSink;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BaseNodeCurrencyService implements ICurrencyService {

    private static final int MAXIMUM_BUFFER_SIZE = 50000;
    public static final Hash NATIVE_CURRENCY_HASH = null;
    private static final String RECOVERY_NODE_GET_CURRENCIES_UPDATE_REACTIVE_ENDPOINT = "/currencies/update/batch";
    private static final int NUMBER_OF_NATIVE_CURRENCY = 1;
    private EnumMap<CurrencyType, HashSet<Hash>> currencyHashByTypeMap;
    private CurrencyData nativeCurrencyData;
    @Autowired
    protected Currencies currencies;
    @Autowired
    private INetworkService networkService;
    @Autowired
    protected RestTemplate restTemplate;
    @Autowired
    protected ApplicationContext applicationContext;
    @Autowired
    private GetUpdatedCurrencyRequestCrypto getUpdatedCurrencyRequestCrypto;
    @Autowired
    private CurrencyTypeRegistrationCrypto currencyTypeRegistrationCrypto;
    @Autowired
    private HttpJacksonSerializer jacksonSerializer;
    @Autowired
    private CurrencyRegistrarCrypto currencyRegistrarCrypto;
    @Autowired
    private IChunkService chunkService;

    public void init() {
        log.info("{} is up", this.getClass().getSimpleName());
        currencyHashByTypeMap = new EnumMap<>(CurrencyType.class);
        nativeCurrencyData = null;
        updateCurrencyHashByTypeMapFromExistingCurrencies();
        updateCurrencies();
    }

    public void updateCurrencyHashByTypeMapFromExistingCurrencies() {
        currencies.forEach(this::updateCurrencyHashByTypeMap);
        if (!currencies.isEmpty()) {
            verifyValidNativeCurrencyPresent();
        }
    }

    protected void updateCurrencyHashByTypeMap(CurrencyData currencyData) {
        CurrencyType currencyType = currencyData.getCurrencyTypeData().getCurrencyType();
        currencyHashByTypeMap.putIfAbsent(currencyType, new HashSet<>());
        currencyHashByTypeMap.get(currencyType).add(currencyData.getHash());
    }

    protected HashSet getCurrencyHashesByCurrencyType(CurrencyType currencyType) {
        return currencyHashByTypeMap.get(currencyType);
    }

    @Override
    public void updateCurrencies() {
        GetUpdatedCurrencyRequest getUpdatedCurrencyRequest = new GetUpdatedCurrencyRequest();
        getUpdatedCurrencyRequest.setCurrencyHashesByType(currencyHashByTypeMap);
        getUpdatedCurrencyRequestCrypto.signMessage(getUpdatedCurrencyRequest);
        getUpdatedCurrencyDataFromRecoveryServer(getUpdatedCurrencyRequest);

        verifyValidNativeCurrencyPresent();
    }

    private void getUpdatedCurrencyDataFromRecoveryServer(GetUpdatedCurrencyRequest getUpdatedCurrencyRequest) {
        CustomRequestCallBack requestCallBack = new CustomRequestCallBack(jacksonSerializer, getUpdatedCurrencyRequest);
        ResponseExtractor responseExtractor = chunkService.getResponseExtractor(currencyData -> currencyDataFromRecoveryServiceHandler((CurrencyData) currencyData), MAXIMUM_BUFFER_SIZE);
        restTemplate.execute(networkService.getRecoveryServerAddress() + RECOVERY_NODE_GET_CURRENCIES_UPDATE_REACTIVE_ENDPOINT,
                HttpMethod.POST, requestCallBack, responseExtractor);
    }

    private void currencyDataFromRecoveryServiceHandler(CurrencyData currencyData) {
        CurrencyData originalCurrencyData = currencies.getByHash(currencyData.getHash());
        if (originalCurrencyData != null) {
            replaceExistingCurrencyDataDueToTypeChange(originalCurrencyData, currencyData);
        } else {
            putCurrencyData(currencyData);
        }
    }

    protected void replaceExistingCurrencyDataDueToTypeChange(CurrencyData originalCurrencyData, CurrencyData recoveredCurrencyData) {
        CurrencyType originalCurrencyType = originalCurrencyData.getCurrencyTypeData().getCurrencyType();
        currencies.delete(originalCurrencyData);
        currencies.put(recoveredCurrencyData);
        currencyHashByTypeMap.get(originalCurrencyType).remove(originalCurrencyData.getHash());
        updateCurrencyHashByTypeMap(recoveredCurrencyData);
    }

    private void verifyValidNativeCurrencyPresent() {
        HashSet<Hash> nativeCurrencyHashes = currencyHashByTypeMap.get(CurrencyType.NATIVE_COIN);
        if (nativeCurrencyHashes == null || nativeCurrencyHashes.isEmpty() || nativeCurrencyHashes.size() != NUMBER_OF_NATIVE_CURRENCY) {
            throw new CurrencyInitializationException("Failed to retrieve native currency data");
        } else {
            CurrencyData nativeCurrency = currencies.getByHash(nativeCurrencyHashes.iterator().next());
            if (!currencyRegistrarCrypto.verifySignature(nativeCurrency)) {
                throw new CurrencyInitializationException("Failed to verify native currency data of " + nativeCurrency.getHash());
            } else {
                CurrencyTypeRegistrationData nativeCurrencyTypeRegistrationData = new CurrencyTypeRegistrationData(nativeCurrency);
                if (!currencyTypeRegistrationCrypto.verifySignature(nativeCurrencyTypeRegistrationData)) {
                    throw new CurrencyInitializationException("Failed to verify native currency data type of " + nativeCurrencyTypeRegistrationData.getCurrencyType().getText());
                }
            }
            if (getNativeCurrency() == null) {
                setNativeCurrencyData(nativeCurrency);
            }
        }
    }

    protected void setNativeCurrencyData(CurrencyData currencyData) {
        if (this.nativeCurrencyData != null) {
            throw new CurrencyInitializationException("Attempted to override existing native currency");
        }
        this.nativeCurrencyData = currencyData;
    }

    public CurrencyData getNativeCurrency() {
        return this.nativeCurrencyData;
    }

    public void getUpdatedCurrencyBatch(GetUpdatedCurrencyRequest getUpdatedCurrencyRequest, FluxSink<CurrencyData> fluxSink) {
        if (!getUpdatedCurrencyRequestCrypto.verifySignature(getUpdatedCurrencyRequest)) {
            log.error("Authorization check failed on request to get updated currencies.");
        }
        Map<CurrencyType, HashSet<Hash>> existingCurrencyHashesByType = getUpdatedCurrencyRequest.getCurrencyHashesByType();
        getRequiringUpdateOfCurrencyDataByType(existingCurrencyHashesByType, fluxSink);

    }

    @Override
    public BigDecimal getTokenTotalSupply(Hash currencyHash) {
        CurrencyData currency = currencies.getByHash(currencyHash);
        if (currency == null) {
            throw new CurrencyNotFoundException(String.format("Currency with hash %s was not found", currencyHash));
        }
        return currencies.getByHash(currencyHash).getTotalSupply();
    }

    @Override
    public int getTokenScale(Hash currencyHash) {
        CurrencyData currency = currencies.getByHash(currencyHash);
        if (currency == null) {
            throw new CurrencyNotFoundException(String.format("Currency with hash %s was not found", currencyHash));
        }
        return currencies.getByHash(currencyHash).getScale();
    }

    private void getRequiringUpdateOfCurrencyDataByType(Map<CurrencyType, HashSet<Hash>> existingCurrencyHashesByType, FluxSink<CurrencyData> fluxSink) {
        currencyHashByTypeMap.forEach((localCurrencyType, localCurrencyHashes) -> {
            HashSet<Hash> existingCurrencyHashes = existingCurrencyHashesByType.get(localCurrencyType);

            localCurrencyHashes.forEach(localCurrencyHash -> {
                if (existingCurrencyHashes == null || existingCurrencyHashes.isEmpty() ||
                        !existingCurrencyHashes.contains(localCurrencyHash)) {
                    sendUpdatedCurrencyData(localCurrencyHash, fluxSink);
                }
            });
        });
        fluxSink.complete();
    }

    private void sendUpdatedCurrencyData(Hash localCurrencyHash, FluxSink<CurrencyData> fluxSink) {
        CurrencyData updatedCurrencyData = currencies.getByHash(localCurrencyHash);
        if (updatedCurrencyData != null) {
            fluxSink.next(updatedCurrencyData);
        } else {
            log.error("Failed to retrieve currencyData {}", localCurrencyHash);
        }
    }

    @Override
    public void putCurrencyData(CurrencyData currencyData) {
        if (currencyData == null) {
            log.error("Failed to add an empty currency");
            return;
        }
        currencies.put(currencyData);
        updateCurrencyHashByTypeMap(currencyData);
    }


    public boolean verifyCurrencyExists(Hash currencyDataHash) {
        return currencies.getByHash(currencyDataHash) != null;
    }

    public void setCurrencyDataName(CurrencyData currencyData, String name) {
        if (name.length() != name.trim().length()) {
            log.error("Attempted to set an invalid currency name with spaces at the start or the end {}.", name);
            return;
        }
        final String[] words = name.split(" ");
        for (String word : words) {
            if (word == null || word.isEmpty() || !Pattern.compile("[A-Za-z0-9]+").matcher(word).matches()) {
                log.error("Attempted to set an invalid currency name with the word {}.", name);
                return;
            }
        }
        currencyData.setName(name);
    }

    public void setCurrencyDataSymbol(CurrencyData currencyData, String symbol) {
        if (!Pattern.compile("[A-Z]{0,15}").matcher(symbol).matches()) {
            log.error("Attempted to set an invalid currency symbol of {}.", symbol);
            return;
        }
        currencyData.setSymbol(symbol);
    }

    //TODO 9/10/2019 astolia/tomer:  handle crypto
    protected CurrencyData createCurrencyData(String name, String symbol, BigDecimal totalSupply, int scale, Instant creationTime,
                                              String description, CurrencyType currencyType) {
        CurrencyData currencyData = new CurrencyData();
        setCurrencyDataName(currencyData, name);
        setCurrencyDataSymbol(currencyData, symbol);
        currencyData.setHash();
        currencyData.setTotalSupply(totalSupply);
        currencyData.setScale(scale);
        currencyData.setCreationTime(creationTime);
        currencyData.setDescription(description);

        CurrencyTypeData currencyTypeData = new CurrencyTypeData(currencyType, Instant.now());
        currencyData.setCurrencyTypeData(currencyTypeData);
        CurrencyTypeRegistrationData currencyTypeRegistrationData = new CurrencyTypeRegistrationData(currencyData);
        currencyTypeRegistrationCrypto.signMessage(currencyTypeRegistrationData);
        currencyTypeData.setSignature(currencyTypeRegistrationData.getSignature());
        currencyRegistrarCrypto.signMessage(currencyData);
        return currencyData;
    }

}