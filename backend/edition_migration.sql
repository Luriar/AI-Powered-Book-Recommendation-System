-- ① 컬럼 추가 (NULL 허용)
ALTER TABLE book
    ADD COLUMN edition VARCHAR(20) NULL DEFAULT NULL COMMENT '개정판';

-- ② 그룹별 순번 → edition 채우기
SET @rownum := 0;
SET @prev_t := ''; SET @prev_a := '';

UPDATE book b
    JOIN (
    SELECT id,
    CASE
    WHEN @prev_t = title AND @prev_a = author
    THEN @rownum := @rownum + 1
    ELSE (@rownum := 1)
    END AS seq,
    @prev_t := title  AS t,
    @prev_a := author AS a
    FROM book
    ORDER BY title, author, id
    ) x ON b.id = x.id
    SET b.edition = CONCAT(x.seq, '판');

-- ③ NOT NULL + UNIQUE 제약
ALTER TABLE book
    MODIFY edition VARCHAR(20) NOT NULL,
    ADD CONSTRAINT uk_title_author_edition
    UNIQUE (title, author, edition);
