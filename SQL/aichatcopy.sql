/*
Navicat MySQL Data Transfer

Source Server         : localhost_3306
Source Server Version : 80046
Source Host           : localhost:3306
Source Database       : aichatcopy

Target Server Type    : MYSQL
Target Server Version : 80046
File Encoding         : 65001

Date: 2026-06-13 13:32:28
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for chat_messages
-- ----------------------------
DROP TABLE IF EXISTS `chat_messages`;
CREATE TABLE `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_reply` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `timestamp` datetime NOT NULL,
  `user_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc8ljv426x8fj9tcywei40stu9` (`conversation_id`),
  KEY `FK6f0y4l43ihmgfswkgy9yrtjkh` (`user_id`),
  CONSTRAINT `FK6f0y4l43ihmgfswkgy9yrtjkh` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKc8ljv426x8fj9tcywei40stu9` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of chat_messages
-- ----------------------------
INSERT INTO `chat_messages` VALUES ('123', '（伸了个懒腰，耳朵轻轻抖动）喵~你好呀！今天阳光真好，要不要一起晒太阳？', '2026-06-12 20:56:53', '你好', '71', '1');

-- ----------------------------
-- Table structure for conversations
-- ----------------------------
DROP TABLE IF EXISTS `conversations`;
CREATE TABLE `conversations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpltqvfcbkql9svdqwh0hw4g1d` (`user_id`),
  CONSTRAINT `FKpltqvfcbkql9svdqwh0hw4g1d` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of conversations
-- ----------------------------
INSERT INTO `conversations` VALUES ('71', '2026-06-12 20:56:41', '你好', '1');

-- ----------------------------
-- Table structure for model_configs
-- ----------------------------
DROP TABLE IF EXISTS `model_configs`;
CREATE TABLE `model_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `api_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `display_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `input_token_price` decimal(12,6) NOT NULL,
  `output_token_price` decimal(12,6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of model_configs
-- ----------------------------
INSERT INTO `model_configs` VALUES ('1', '', 'https://api.deepseek.com/v1/chat/completions', 'deepseek-v4-flash', '0', null, '0.001500', '0.002500');
INSERT INTO `model_configs` VALUES ('5', '', 'https://jeniya.cn/v1/chat/completions', 'grok-4.1', '0', null, '0.002500', '0.015000');
INSERT INTO `model_configs` VALUES ('6', '', 'https://jeniya.cn/v1/chat/completions', 'gemini-3-flash-preview', '0', null, '0.001200', '0.005000');
INSERT INTO `model_configs` VALUES ('7', '', 'https://jeniya.cn/v1/chat/completions', 'gpt-5.5', '0', null, '0.003500', '0.018000');

-- ----------------------------
-- Table structure for prompts
-- ----------------------------
DROP TABLE IF EXISTS `prompts`;
CREATE TABLE `prompts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7h41xxu11jsc98ndygogbgt3d` (`user_id`),
  CONSTRAINT `FK7h41xxu11jsc98ndygogbgt3d` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of prompts
-- ----------------------------
INSERT INTO `prompts` VALUES ('1', '你是一个猫娘', '猫', '1');

-- ----------------------------
-- Table structure for prompts_hub
-- ----------------------------
DROP TABLE IF EXISTS `prompts_hub`;
CREATE TABLE `prompts_hub` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  `likes_count` int DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `user_id` bigint NOT NULL,
  `user_message` varchar(500) DEFAULT NULL,
  `user_name` varchar(50) DEFAULT NULL,
  `featured` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of prompts_hub
-- ----------------------------
INSERT INTO `prompts_hub` VALUES ('1', '你是一个猫娘', '2026-06-08 18:36:36.080622', null, '56', '猫', '1', '一个还不错的提示词', 'testuser', null);

-- ----------------------------
-- Table structure for recharge_orders
-- ----------------------------
DROP TABLE IF EXISTS `recharge_orders`;
CREATE TABLE `recharge_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(12,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `pay_channel` varchar(20) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `third_party_order_id` varchar(64) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `review_comment` varchar(500) DEFAULT NULL,
  `review_status` varchar(20) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `sponsor_image_path` varchar(500) DEFAULT NULL,
  `user_name` varchar(50) DEFAULT NULL,
  `user_pid` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKcl81rvd779docswql4w3t4ans` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of recharge_orders
-- ----------------------------

-- ----------------------------
-- Table structure for token_usages
-- ----------------------------
DROP TABLE IF EXISTS `token_usages`;
CREATE TABLE `token_usages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `balance_after` decimal(12,4) NOT NULL,
  `balance_before` decimal(12,4) NOT NULL,
  `conversation_id` bigint NOT NULL,
  `cost_amount` decimal(12,4) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `input_tokens` bigint NOT NULL,
  `model_config_id` bigint NOT NULL,
  `model_name` varchar(255) NOT NULL,
  `output_tokens` bigint NOT NULL,
  `total_tokens` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of token_usages
-- ----------------------------
INSERT INTO `token_usages` VALUES ('4', '9.9994', '10.0000', '61', '0.0006', '2026-06-11 00:38:00.681377', '5', '1', 'deepseek-v4-flash', '239', '244', '1');
INSERT INTO `token_usages` VALUES ('5', '9.9987', '9.9994', '61', '0.0007', '2026-06-11 00:45:37.990591', '147', '1', 'deepseek-v4-flash', '192', '339', '1');
INSERT INTO `token_usages` VALUES ('6', '9.9970', '9.9987', '61', '0.0017', '2026-06-11 00:48:46.896386', '213', '1', 'deepseek-v4-flash', '546', '759', '1');
INSERT INTO `token_usages` VALUES ('7', '9.9966', '9.9970', '62', '0.0004', '2026-06-11 00:49:50.286018', '5', '1', 'deepseek-v4-flash', '144', '149', '1');
INSERT INTO `token_usages` VALUES ('8', '9.9958', '9.9966', '62', '0.0008', '2026-06-11 00:51:48.186010', '42', '1', 'deepseek-v4-flash', '308', '350', '1');
INSERT INTO `token_usages` VALUES ('9', '9.9951', '9.9958', '62', '0.0007', '2026-06-11 16:51:47.275563', '170', '1', 'deepseek-v4-flash', '190', '360', '1');
INSERT INTO `token_usages` VALUES ('10', '9.9944', '9.9951', '65', '0.0007', '2026-06-12 20:33:48.355961', '3', '5', 'grok-4.1', '43', '46', '1');
INSERT INTO `token_usages` VALUES ('11', '9.9943', '9.9944', '66', '0.0001', '2026-06-12 20:34:22.544539', '3', '6', 'gemini-3-flash-preview', '18', '21', '1');
INSERT INTO `token_usages` VALUES ('12', '9.9940', '9.9943', '67', '0.0003', '2026-06-12 20:34:35.539988', '3', '7', 'gpt-5.5', '18', '21', '1');
INSERT INTO `token_usages` VALUES ('13', '9.9936', '9.9940', '68', '0.0004', '2026-06-12 20:46:42.939808', '5', '1', 'deepseek-v4-flash', '147', '152', '1');
INSERT INTO `token_usages` VALUES ('14', '9.9899', '9.9936', '68', '0.0037', '2026-06-12 20:47:07.003758', '1427', '1', 'deepseek-v4-flash', '640', '2067', '1');
INSERT INTO `token_usages` VALUES ('15', '9.9870', '9.9899', '69', '0.0029', '2026-06-12 20:47:24.883083', '1438', '1', 'deepseek-v4-flash', '288', '1726', '1');
INSERT INTO `token_usages` VALUES ('16', '10.9867', '10.9870', '70', '0.0003', '2026-06-12 20:49:17.000676', '9', '1', 'deepseek-v4-flash', '130', '139', '1');
INSERT INTO `token_usages` VALUES ('17', '10.9863', '10.9867', '71', '0.0004', '2026-06-12 20:56:53.479437', '9', '1', 'deepseek-v4-flash', '138', '147', '1');
INSERT INTO `token_usages` VALUES ('18', '10.9834', '10.9863', '72', '0.0029', '2026-06-12 20:57:11.566196', '1443', '1', 'deepseek-v4-flash', '283', '1726', '1');

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `pid` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `failed_attempts` int DEFAULT '0',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_encrypted` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `balance` decimal(12,4) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES ('1', '$2a$10$UrtGKxmgzlYpBmpFLLP2l.wmIXDj4Zj2vV.MyncaPabwN0uWq7SAO', 'testuser', '000001', '0', 'test@qq.com', null, '11.9834', '', 'USER', '2026-06-11 16:18:32', '24');
INSERT INTO `users` VALUES ('4', '$2a$10$n4Q.V0DbbufVTyTrJh6TbOI5tyFecU1YLCJ5Uvm4ELmLAxVbHdcJ2', 'admin', '999999', '0', 'admin@aichat.com', null, '10.0000', '', 'ADMIN', '2026-06-11 16:22:12', '2');
