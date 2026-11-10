INSERT INTO stock_balance(id,warehouse_id,product_id,on_hand,reserved,blocked,last_movement_at)
SELECT nextval('wholesale_seq'),w.id,p.id,
       CASE WHEN w.code='EAST' THEN
         CASE p.code WHEN 'P001' THEN 300 WHEN 'P002' THEN 24 WHEN 'P003' THEN 0 ELSE 60 END
       ELSE CASE p.code WHEN 'P001' THEN 48 WHEN 'P002' THEN 120 ELSE 12 END END,
       0,false,CURRENT_TIMESTAMP
FROM warehouse w CROSS JOIN product p
WHERE w.active=true;

INSERT INTO stock_movement(id,balance_id,movement_type,quantity_change,reserved_change,
    on_hand_after,reserved_after,document_type,document_id,document_number,actor,note,occurred_at)
SELECT nextval('wholesale_seq'),b.id,'OPENING',b.on_hand,0,b.on_hand,0,
       'OpeningBalance',b.id,'OPEN-' || b.id,'seed','デモ開始残高',CURRENT_TIMESTAMP
FROM stock_balance b
WHERE b.on_hand>0;
