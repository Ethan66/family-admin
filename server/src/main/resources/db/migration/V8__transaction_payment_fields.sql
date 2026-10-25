ALTER TABLE transaction_record
    ADD COLUMN bank_card VARCHAR(20) NULL COMMENT '银行卡或支付账户',
    ADD COLUMN platform VARCHAR(20) NULL COMMENT '消费平台';
