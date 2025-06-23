-- 테이블 생성
CREATE TABLE products (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      name VARCHAR(255) NOT NULL,
      price INT NOT NULL,
      quantity INT NOT NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

DELETE FROM products;
TRUNCATE TABLE products;

SELECT * from products;

-- 테이블 데이터 삽입
INSERT INTO products (name, price, quantity)
    VALUES ('딸기', 7000, 10), ('아보카도', 10000, 32);

-- 인덱스 추가
ALTER TABLE products ADD INDEX idx_products_name (name);

SHOW INDEX FROM products;
SHOW INDEX FROM products WHERE Column_name = 'name';




