-- 建資料庫（如果 docker-compose 沒設定 MariaDB_DATABASE，也可以寫）
CREATE DATABASE IF NOT EXISTS interviewworks;

USE interviewworks;

-- interviewworks.secret definition

CREATE TABLE IF NOT EXISTS `secret` (
                          `secret_number` varchar(100) NOT NULL,
                          `update_date` varchar(100) NOT NULL,
                          PRIMARY KEY (`update_date`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

-- interviewworks.userdata definition

CREATE TABLE IF NOT EXISTS `userdata` (
                            `username` varchar(100) NOT NULL,
                            `password` varchar(100) NOT NULL,
                            `update_date` varchar(100) NOT NULL,
                            `time_stamp` TIMESTAMP NOT NULL,
                            `token_temp` varchar(500) DEFAULT NULL,
                            PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;