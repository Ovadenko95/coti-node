package io.coti.basenode.data;

import io.coti.basenode.crypto.CryptoHelper;
import io.coti.basenode.data.interfaces.IPropagatable;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;

@Data
public class InitiatedTokenNoticeData implements IPropagatable {

    @NotNull
    private @Valid Hash hash;
    @NotNull
    private @Valid CurrencyData currencyData;
    @NotNull
    private @Valid ClusterStampNameData clusterStampNameData;

    public InitiatedTokenNoticeData(CurrencyData currencyData, ClusterStampNameData clusterStampNameData) {
        this.currencyData = currencyData;
        this.clusterStampNameData = clusterStampNameData;
        setHash();
    }

    public void setHash() {
        byte[] currencyHashInBytes = this.currencyData.getHash().getBytes();
        byte[] clusterStampNameDataInBytes = this.getClusterStampNameData().getHash().getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(currencyHashInBytes.length + clusterStampNameDataInBytes.length)
                .put(currencyHashInBytes).put(clusterStampNameDataInBytes);
        CryptoHelper.cryptoHash(buffer.array());
    }
}
