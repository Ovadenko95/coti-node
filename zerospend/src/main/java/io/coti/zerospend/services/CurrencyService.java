package io.coti.zerospend.services;

import io.coti.basenode.crypto.OriginatorCurrencyCrypto;
import io.coti.basenode.data.CurrencyData;
import io.coti.basenode.data.CurrencyType;
import io.coti.basenode.data.CurrencyTypeData;
import io.coti.basenode.data.CurrencyTypeRegistrationData;
import io.coti.basenode.services.BaseNodeCurrencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
public class CurrencyService extends BaseNodeCurrencyService {

    @Value("${native.currency.name}")
    private String nativeCurrencyName;
    @Value("${native.currency.symbol}")
    private String nativeCurrencySymbol;
    @Value("${native.currency.supply}")
    private BigDecimal nativeCurrencyTotalSupply;
    @Value("${native.currency.scale}")
    private int nativeCurrencyScale;
    @Value("${native.currency.description}")
    private String nativeCurrencyDescription;
    @Autowired
    private OriginatorCurrencyCrypto originatorCurrencyCrypto;

    @Override
    public void updateCurrencies() {
        CurrencyData nativeCurrencyData = getNativeCurrency();
        if (nativeCurrencyData == null) {
            generateNativeCurrency();
        }
    }

    @Override
    public void generateNativeCurrency() {
        CurrencyData currencyData = new CurrencyData();
        Instant createTime = Instant.now();
        currencyData.setName(nativeCurrencyName);
        currencyData.setSymbol(nativeCurrencySymbol.toUpperCase());
        currencyData.setTotalSupply(nativeCurrencyTotalSupply);
        currencyData.setScale(nativeCurrencyScale);
        currencyData.setCreateTime(createTime);
        currencyData.setDescription(nativeCurrencyDescription);
        currencyData.setHash();
        CurrencyTypeRegistrationData currencyTypeRegistrationData = new CurrencyTypeRegistrationData(currencyData.getHash(), CurrencyType.NATIVE_COIN, createTime);
        currencyTypeRegistrationCrypto.signMessage(currencyTypeRegistrationData);
        currencyData.setCurrencyTypeData(currencyTypeRegistrationData);

        originatorCurrencyCrypto.signMessage(currencyData);

        putCurrencyData(currencyData);
        setNativeCurrencyData(currencyData);
    }
}