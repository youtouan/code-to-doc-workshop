INSERT INTO app_user(id,login,display_name,password_hash,roles,active) VALUES
  (101,'sales','営業デモ','pbkdf2-sha1$120000$b9e3823c5567f7852b71e72779a2743617c0c68bf00bd86a$19a64eea7a918ba476f77c6f43e4ce3a8c02587c732a8520d8de41b55acc51e4','SALES',true),
  (102,'manager','承認者デモ','pbkdf2-sha1$120000$5dcdb8ccbffc389b0f8f301595c03a885cceea3a0bf37ca5$ce2843a61d0bbebd60416548689bc8060562424a4903033aecf4b8bc95980f63','MANAGER',true),
  (103,'warehouse','倉庫デモ','pbkdf2-sha1$120000$dbd25abfe54f70f41056ff3b7caba9b46baeb12e50901e7a$9b788d68279f767a816877fdb2a0e0886bce57a858947a1d04266ab8024b2679','WAREHOUSE',true),
  (104,'billing','経理デモ','pbkdf2-sha1$120000$5c7e7b78eca4241f32f24613413ddfc927a0addc4ec4edb4$08acd2851242d80bf5d1d5dd3c0692558a4bb5beec18135243370c946e374e70','BILLING',true),
  (105,'admin','管理者デモ','pbkdf2-sha1$120000$717673093a3a4ea257451b1eb7ef180c5b53f71866d68a79$ab0f6be1a3c0f956de7a5e0cab3538631aff0cc97059ff7295018242ce23536e','ADMIN',true),
  (106,'batch','バッチデモ','pbkdf2-sha1$120000$a4488d48e7d5f0684ec81cd959081ad0464c6a29abc44acb$59b5806f268b72d4941323a6a35f48db2d8d2165e652efd14118119e9b88629d','BATCH',true);
