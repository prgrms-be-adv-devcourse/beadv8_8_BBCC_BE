ALTER TABLE product.product DROP CONSTRAINT IF EXISTS product_status_check;
ALTER TABLE product.product ADD CONSTRAINT product_status_check
    CHECK (status IN ('SALE_PENDING', 'ON_SALE', 'STOPPED', 'DELISTED'));
