-- Fictional suppliers only. No inventory is inserted here; receipts use InventoryService.
INSERT INTO supplier
    (id,code,name,closing_day,payment_term_days,default_lead_time_days,minimum_order_amount,
     tax_rounding,address,ordering_instructions,notes)
VALUES
    (41,'S001','架空はばたき紙業',31,30,3,0.00,'DOWN','架空県はばたき市見本工業町1-1',
        '見本の発注番号を納品書に記載してください。','研修用の架空仕入先'),
    (42,'S002','架空しずく食品卸',20,30,2,0.00,'HALF_UP','架空県しずく市見本流通町2-2',
        '検品不良は良品数量に含めず、不良理由を記録してください。','研修用の架空仕入先'),
    (43,'S003','架空いろどり資材',10,60,5,1000.00,'DOWN','架空県いろどり市見本流通町3-3',
        '最低発注金額1000円。入数単位でご注文ください。','研修用の架空仕入先'),
    (44,'S004','架空発注停止サプライ',31,30,7,0.00,'DOWN','',
        '発注停止の制御確認用。','研修用の架空仕入先');
UPDATE supplier SET on_hold = TRUE WHERE id = 44;

INSERT INTO supplier_product
    (id,supplier_id,product_id,supplier_product_code,valid_from,minimum_quantity,order_pack_size,
     lead_time_days,unit_cost,preferred,notes)
VALUES
    (51,41,11,'DEMO-A4',DATE '2020-01-01',10,10,3,320.00,TRUE,'通常仕入'),
    (52,41,11,'DEMO-A4',DATE '2020-01-01',200,10,3,305.00,TRUE,'200冊以上の数量割引'),
    (53,41,12,'DEMO-PEN',DATE '2020-01-01',10,10,3,65.00,TRUE,'10本単位'),
    (54,41,20,'DEMO-FILE',DATE '2020-01-01',10,10,3,130.00,TRUE,'10冊単位'),
    (55,42,13,'DEMO-TEA',DATE '2020-01-01',24,24,2,90.00,TRUE,'24本ケース'),
    (56,42,13,'DEMO-TEA',DATE '2020-01-01',240,24,2,85.00,TRUE,'10ケース以上'),
    (57,42,16,'DEMO-WATER',DATE '2020-01-01',6,6,2,95.00,TRUE,'6本ケース'),
    (58,42,17,'DEMO-BISCUIT',DATE '2020-01-01',12,12,4,210.00,TRUE,'12箱単位'),
    (59,43,14,'DEMO-BOX',DATE '2020-01-01',10,10,5,110.00,TRUE,'10枚単位'),
    (60,43,15,'DEMO-TAPE',DATE '2020-01-01',6,6,5,170.00,TRUE,'6巻単位'),
    (61,43,18,'DEMO-CLOTH',DATE '2020-01-01',10,10,5,250.00,TRUE,'10袋単位'),
    (62,43,19,'DEMO-GLOVE',DATE '2020-01-01',10,10,5,380.00,TRUE,'10双単位'),
    (63,43,11,'ALT-A4',DATE '2020-01-01',20,10,5,315.00,FALSE,'第二仕入先・最小数と優先順位の確認用'),
    (64,44,11,'HOLD-A4',DATE '2020-01-01',10,10,7,299.00,FALSE,'停止仕入先は提案対象外');

INSERT INTO purchase_order
    (id,number,supplier_id,warehouse_id,order_date,expected_date,status,supplier_name,
     closing_day,payment_term_days,tax_rounding,ordering_instructions,total_amount,created_by,
     created_at,last_changed_by,last_changed_at,notes)
VALUES
    (71,'PO-00000071',41,21,CURRENT_DATE - 2,CURRENT_DATE + 1,'DRAFT','架空はばたき紙業',
     31,30,'DOWN','見本の発注番号を納品書に記載してください。',6400.00,'warehouse',
     CURRENT_TIMESTAMP,'warehouse',CURRENT_TIMESTAMP,'下書き発注の操作確認用・架空取引');
INSERT INTO purchase_order_line
    (id,order_id,line_number,product_id,product_code,product_name,unit,supplier_product_code,
     quantity,order_pack_size,lead_time_days,unit_cost,line_amount,expected_date,notes)
VALUES
    (72,71,1,11,'P001','架空コピー用紙 A4 500枚','冊','DEMO-A4',20,10,3,320.00,6400.00,
     CURRENT_DATE + 1,'申請後、作成者とは別の管理者で承認してください。');
