-- All names and addresses below are fictional workshop data, not real business contacts.
INSERT INTO customer
    (id,code,name,credit_limit,closing_day,payment_term_days,tax_rounding,address,notes)
VALUES
    (1,'C001','架空つばめ商店',1500000.00,31,30,'DOWN','架空県つばめ市見本町1-1','研修用の架空企業'),
    (2,'C002','架空みどり食品',800000.00,20,30,'HALF_UP','架空県みどり市見本町2-2','軽減税率商品を扱う架空企業'),
    (3,'C003','架空あおぞら事務用品',1200000.00,10,60,'UP','架空県あおぞら市見本町3-3','研修用の架空企業'),
    (4,'C004','架空こもれび雑貨',300000.00,31,0,'DOWN','架空県こもれび市見本町4-4','即日支払条件の架空企業'),
    (5,'C005','架空休止商会',0.00,31,30,'DOWN','','利用停止状態の確認用'),
    (6,'C006','架空保留商事',100000.00,20,30,'DOWN','','与信保留状態の確認用');
UPDATE customer SET active = FALSE WHERE id = 5;
UPDATE customer SET on_hold = TRUE WHERE id = 6;

INSERT INTO product
    (id,code,name,unit,tax_category,list_price,standard_cost,pack_size,reorder_point,reorder_quantity,notes)
VALUES
    (11,'P001','架空コピー用紙 A4 500枚','冊','STANDARD',500.00,320.00,1,100,200,'研修用・紙製品'),
    (12,'P002','架空ボールペン 黒','本','STANDARD',120.00,65.00,1,150,300,'研修用・事務用品'),
    (13,'P003','架空緑茶 500ml','本','REDUCED',150.00,90.00,1,120,240,'研修用・飲料'),
    (14,'P004','架空段ボール M','枚','STANDARD',180.00,110.00,10,50,100,'研修用・10枚単位'),
    (15,'P005','架空布テープ 50mm','巻','STANDARD',280.00,170.00,1,40,60,'研修用・梱包用品'),
    (16,'P006','架空ミネラル水 2L','本','REDUCED',160.00,95.00,6,60,120,'研修用・6本単位'),
    (17,'P007','架空保存ビスケット','箱','REDUCED',350.00,210.00,1,30,60,'研修用・保存食'),
    (18,'P008','架空除菌クロス','袋','STANDARD',420.00,250.00,1,40,80,'研修用・清掃用品'),
    (19,'P009','架空保護手袋 M','双','STANDARD',600.00,380.00,1,20,40,'研修用・作業用品'),
    (20,'P010','架空収納ファイル A4','冊','STANDARD',240.00,130.00,1,60,120,'研修用・事務用品'),
    (23,'P011','架空貸与備品台帳','冊','EXEMPT',0.00,0.00,1,0,0,'非課税・無償品の操作確認用'),
    (24,'P012','架空旧型ラベル','巻','STANDARD',300.00,180.00,1,0,0,'廃番商品の確認用');
UPDATE product SET active = FALSE WHERE id = 24;

INSERT INTO warehouse(id,code,name,address) VALUES
    (21,'EAST','架空東物流センター','架空県東市物流見本町1-1'),
    (22,'WEST','架空西物流センター','架空県西市物流見本町2-2');

INSERT INTO price_agreement
    (id,customer_id,product_id,valid_from,valid_to,minimum_quantity,unit_price,notes)
VALUES
    (31,1,11,DATE '2020-01-01',NULL,1,470.00,'架空基本契約'),
    (32,1,11,DATE '2020-01-01',NULL,100,440.00,'架空大口100冊以上'),
    (33,2,13,DATE '2020-01-01',NULL,24,138.00,'架空飲料大口契約'),
    (34,3,12,DATE '2020-01-01',NULL,50,108.00,'架空事務用品契約'),
    (35,1,15,DATE '2020-01-01',DATE '2020-12-31',1,250.00,'終了済み契約の確認用');
