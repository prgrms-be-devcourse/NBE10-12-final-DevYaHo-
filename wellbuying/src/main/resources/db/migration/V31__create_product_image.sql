CREATE TABLE product_image (
    id         BIGSERIAL    PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT          NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_image_product_id ON product_image (product_id, sort_order);
