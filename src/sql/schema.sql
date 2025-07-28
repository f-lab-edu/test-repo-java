-- 테이블 생성
CREATE TABLE products (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      name VARCHAR(255) NOT NULL,
      price INT NOT NULL,
      quantity INT NOT NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE event_participation_log (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     product_id BIGINT NOT NULL,
     user_id BIGINT NOT NULL,
     status VARCHAR(20) NOT NULL,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dead_letter_log (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     topic VARCHAR(255) NOT NULL,
     payload TEXT NOT NULL,
     reason TEXT,
     created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE dead_letter_success_log (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     topic VARCHAR(255) NOT NULL,
     payload TEXT NOT NULL,
     success_at DATETIME NOT NULL
);

CREATE TABLE members (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     name VARCHAR(100),
     email VARCHAR(255) NOT NULL UNIQUE,
     password VARCHAR(255) NOT NULL,
     role VARCHAR(50),
     active BOOLEAN DEFAULT true,
     created_at DATETIME,
     updated_at DATETIME,
     last_login_at DATETIME
);
CREATE INDEX idx_member_email ON members(email);



DELETE FROM products;
TRUNCATE TABLE products;

SELECT * from products;

-- 테이블 데이터 삽입
INSERT INTO products (name, price, quantity)
    VALUES ('딸기', 7000, 10), ('아보카도', 10000, 32);

INSERT INTO members (id, name, email, password, role, active, created_at, updated_at, last_login_at)
VALUES
    (1, '관리자', 'admin@example.com', '{noop}admin123', 'ADMIN', true, now(), now(), now()),
    (2, '사용자', 'user@example.com', '{noop}user123', 'USER', true, now(), now(), now());


INSERT INTO members (id, name, email, password, role, active, created_at, updated_at, last_login_at)
VALUES
    (3, '테스트유저', 'miiniiiiii9@gmail.com', '{noop}test123', 'USER', true, NOW(), NOW(), NOW());



-- 인덱스 추가
ALTER TABLE products ADD INDEX idx_products_name (name);

SHOW INDEX FROM products;
SHOW INDEX FROM products WHERE Column_name = 'name';

UPDATE products SET quantity = 10 WHERE id = 1;








