package io.coti.financialserver.crypto;

import io.coti.basenode.crypto.CryptoHelper;
import io.coti.basenode.crypto.SignatureCrypto;
import io.coti.financialserver.http.data.GetDisputeItemDetailData;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Service
public class GetDisputeItemDetailCrypto extends SignatureCrypto<GetDisputeItemDetailData> {

    @Override
    public byte[] getMessageInBytes(GetDisputeItemDetailData getDisputeItemDetailData) {
        byte[] disputeHashInBytes = getDisputeItemDetailData.getDisputeHash().getBytes();

        ByteBuffer commentDataBuffer = ByteBuffer.allocate(disputeHashInBytes.length + Long.BYTES)
                .put(disputeHashInBytes).putLong(getDisputeItemDetailData.getItemId());

        byte[] commentDataInBytes = commentDataBuffer.array();
        return CryptoHelper.cryptoHash(commentDataInBytes).getBytes();
    }
}