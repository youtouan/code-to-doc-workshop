INSERT INTO stock_transfer
    (id,number,source_warehouse_id,destination_warehouse_id,status,note,
     created_by_id,created_by,created_at,updated_at,cancellation_reason)
VALUES
    (430,'TR-00000430',21,22,'DRAFT','西倉庫向けの補充計画',103,'warehouse',
     CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'');
INSERT INTO stock_transfer_line
    (id,transfer_id,product_id,quantity,dispatched_quantity,received_quantity,lost_quantity,unit_cost)
VALUES
    (431,430,11,12,0,0,0,0),
    (432,430,12,6,0,0,0,0);

INSERT INTO stock_adjustment
    (id,number,warehouse_id,product_id,quantity_change,reason,status,unit_cost,
     proposed_by_id,proposed_by,proposed_at,decision_reason)
SELECT 440,'ADJ-00000440',21,id,-1,'検品で確認した外装破損','PROPOSED',standard_cost,
       103,'warehouse',CURRENT_TIMESTAMP,''
FROM product WHERE id=13;
