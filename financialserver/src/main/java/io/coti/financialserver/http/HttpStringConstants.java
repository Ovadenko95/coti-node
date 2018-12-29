package io.coti.financialserver.http;

import io.coti.basenode.http.BaseNodeHttpStringConstants;

public class HttpStringConstants extends BaseNodeHttpStringConstants {

    public static final String SUCCESS = "Success";

    public static final String DISPUTE_COMMENT_CREATE_UNAUTHORIZED = "Unauthorized dispute comment creation request";
    public static final String DISPUTE_COMMENT_UNAUTHORIZED = "Unauthorized dispute comment request";
    public static final String DISPUTE_DOCUMENT_CREATE_UNAUTHORIZED = "Unauthorized dispute document creation request";
    public static final String DISPUTE_DOCUMENT_UNAUTHORIZED = "Unauthorized dispute document request";
    public static final String DISPUTE_MERCHANT_NOT_FOUND = "Merchant not found";
    public static final String DISPUTE_NOT_FOUND = "Dispute not found";
    public static final String DISPUTE_UNAUTHORIZED = "Unauthorized dispute request";
    public static final String DISPUTE_ITEM_NOT_FOUND = "Dispute item not found";
    public static final String DISPUTE_ITEMS_EXIST_ALREADY = "At least one of the dispute items is already was(or right now) in dispute";
    public static final String DISPUTE_TRANSACTION_NOT_FOUND = "Transaction hash not found";
    public static final String DISPUTE_TRANSACTION_SENDER_INVALID = "Invalid transaction sender";
    public static final String OPEN_DISPUTE_IN_PROCESS_FOR_THIS_TRANSACTION = "Open dispute already in process for this transaction";

    public static final String DOCUMENT_NOT_FOUND = "Document not found";
    public static final String COMMENT_NOT_FOUND = "Comment not found";
    public static final String ITEM_NOT_FOUND = "Item not found";
    public static final String DISPUTE_ITEM_PASSED_RECALL_STATUS = "Dispute item passed recall status";
    public static final String DISPUTE_NOT_IN_CLAIM_STATUS = "Dispute not in claim status";
    public static final String ITEM_NOT_REJECTED_BY_MERCHANT = "Item not rejected by merchant";
    public static final String STATUS_NOT_VALID = "Status not valid";
    public static final String ALREADY_GOT_YOUR_VOTE = "You already voted on this item";

    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_ATTACHMENT_PREFIX = "attachment; filename=";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String S3_SUFFIX_METADATA_KEY = "x-amz-meta-suffix";

    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String INVALID_SIGNATURE = "Invalid signature";
    public static final String INTERNAL_ERROR = "Internal error";
}