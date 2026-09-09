ALTER TABLE product_image ADD COLUMN image_type VARCHAR(20) NOT NULL DEFAULT 'DESCRIPTION';
ALTER TABLE product_image ALTER COLUMN image_type DROP DEFAULT;

DROP INDEX idx_product_image_product_id;
CREATE INDEX idx_product_image_product_id ON product_image (product_id, image_type, sort_order);
