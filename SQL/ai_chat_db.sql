/*
Navicat MySQL Data Transfer

Source Server         : localhost_3306
Source Server Version : 80046
Source Host           : localhost:3306
Source Database       : ai_chat_db

Target Server Type    : MYSQL
Target Server Version : 80046
File Encoding         : 65001

Date: 2026-07-16 16:51:24
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
  KEY `idx_conv_time` (`conversation_id`,`timestamp`),
  CONSTRAINT `FK6f0y4l43ihmgfswkgy9yrtjkh` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKc8ljv426x8fj9tcywei40stu9` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=182 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of chat_messages
-- ----------------------------
INSERT INTO `chat_messages` VALUES ('180', '根据最新的天气信息，今日（2025�?�?5日）重庆�?*晴天**，气温范围在 **32°C ~ 40°C** 之间，空气质�?*�?*，风力为西南�?级。天气炎热，建议您注意防暑降温，尽量减少午后高温时段的外出。如果需要更精确的实时数据，建议您通过当地的天气App或官方气象网站查询�?, '2026-07-15 23:19:21', '今日重庆天气', '103', '1');
INSERT INTO `chat_messages` VALUES ('181', '根据图片描述，这幅画中的角色�?*蕾姆（Rem�?*，她来自知名动漫作品《Re:从零开始的异世界生活》。画面中的她有着标志性的天蓝色短发、齐刘海和蓝色眼眸，头戴粉花发饰和草编遮阳帽，整体色调温暖柔和，突出了她“治愈系”的梦幻气质。如果你对这部作品或角色有更多好奇，我很乐意继续为你介绍~', '2026-07-15 23:20:03', '这张图片里的人物是谁', '103', '1');

-- ----------------------------
-- Table structure for comments
-- ----------------------------
DROP TABLE IF EXISTS `comments`;
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `likes_count` int DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `prompt_id` bigint NOT NULL,
  `reply_to_name` varchar(50) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_prompt` (`prompt_id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of comments
-- ----------------------------
INSERT INTO `comments` VALUES ('13', '你好', '2026-06-14 22:49:13.256587', '0', null, '5', null, '4', 'admin');
INSERT INTO `comments` VALUES ('14', '1', '2026-06-14 23:34:12.218775', '0', null, '1', null, '1', 'testuser');
INSERT INTO `comments` VALUES ('16', '你好', '2026-06-15 00:21:44.061387', '0', '13', '5', 'admin', '1', 'testuser');
INSERT INTO `comments` VALUES ('17', '不错', '2026-06-28 21:53:44.005996', '0', null, '1', null, '1', 'testuser');

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
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of conversations
-- ----------------------------
INSERT INTO `conversations` VALUES ('88', '2026-06-21 01:05:47', '新对�?, '4');
INSERT INTO `conversations` VALUES ('103', '2026-07-15 23:19:04', '今日重庆天气', '1');

-- ----------------------------
-- Table structure for conversation_summaries
-- ----------------------------
DROP TABLE IF EXISTS `conversation_summaries`;
CREATE TABLE `conversation_summaries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `summary` text NOT NULL,
  `message_count_at_generation` int NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `conversation_id` (`conversation_id`),
  CONSTRAINT `conversation_summaries_ibfk_1` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of conversation_summaries
-- ----------------------------

-- ----------------------------
-- Table structure for favorites
-- ----------------------------
DROP TABLE IF EXISTS `favorites`;
CREATE TABLE `favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `prompt_id` bigint NOT NULL COMMENT '提示词ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_prompt` (`user_id`,`prompt_id`),
  UNIQUE KEY `UKhlkqrvfl05dvsbbgav1j8yalv` (`user_id`,`prompt_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_prompt` (`prompt_id`),
  CONSTRAINT `favorites_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `favorites_ibfk_2` FOREIGN KEY (`prompt_id`) REFERENCES `prompts_hub` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of favorites
-- ----------------------------

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of flyway_schema_history
-- ----------------------------
INSERT INTO `flyway_schema_history` VALUES ('1', '1', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', null, 'root', '2026-07-08 22:12:02', '0', '1');
INSERT INTO `flyway_schema_history` VALUES ('2', '2', 'prompts hub community', 'SQL', 'V2__prompts_hub_community.sql', '-151191515', 'root', '2026-07-08 22:12:03', '807', '1');
INSERT INTO `flyway_schema_history` VALUES ('3', '3', 'prompt ratings', 'SQL', 'V3__prompt_ratings.sql', '-1781310699', 'root', '2026-07-08 22:21:27', '73', '1');
INSERT INTO `flyway_schema_history` VALUES ('4', '4', 'seed anime prompts', 'SQL', 'V4__seed_anime_prompts.sql', '-1905269541', 'root', '2026-07-12 00:09:16', '78', '1');
INSERT INTO `flyway_schema_history` VALUES ('5', '1.1', 'token blacklist', 'SQL', 'V1_1__token_blacklist.sql', '634371031', 'root', '2026-07-16 12:28:25', '38', '1');
INSERT INTO `flyway_schema_history` VALUES ('6', '5', 'add last checkin date', 'SQL', 'V5__add_last_checkin_date.sql', '-105783359', 'root', '2026-07-16 12:28:25', '51', '1');

-- ----------------------------
-- Table structure for follows
-- ----------------------------
DROP TABLE IF EXISTS `follows`;
CREATE TABLE `follows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `follower_id` bigint NOT NULL COMMENT '关注者ID',
  `followed_id` bigint NOT NULL COMMENT '被关注者ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followed` (`follower_id`,`followed_id`),
  UNIQUE KEY `UKd6wgm6dc6dkw9knsxjkt1qk87` (`follower_id`,`followed_id`),
  KEY `idx_followed` (`followed_id`),
  KEY `idx_follower` (`follower_id`),
  CONSTRAINT `follows_ibfk_1` FOREIGN KEY (`follower_id`) REFERENCES `users` (`id`),
  CONSTRAINT `follows_ibfk_2` FOREIGN KEY (`followed_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of follows
-- ----------------------------

-- ----------------------------
-- Table structure for friendships
-- ----------------------------
DROP TABLE IF EXISTS `friendships`;
CREATE TABLE `friendships` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `friend_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjwaac0iw9d1fu58mx7afwf9f4` (`user_id`,`friend_id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_friend` (`friend_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of friendships
-- ----------------------------
INSERT INTO `friendships` VALUES ('1', '2026-06-15 00:06:06.839861', '4', 'ACCEPTED', '1');

-- ----------------------------
-- Table structure for friend_messages
-- ----------------------------
DROP TABLE IF EXISTS `friend_messages`;
CREATE TABLE `friend_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `friendship_id` bigint NOT NULL,
  `is_read` bit(1) DEFAULT NULL,
  `receiver_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_friendship_time` (`friendship_id`,`created_at`),
  KEY `idx_receiver_read` (`receiver_id`,`is_read`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of friend_messages
-- ----------------------------
INSERT INTO `friend_messages` VALUES ('1', 'hi', '2026-06-15 00:06:30.055522', '1', '', '1', '4');
INSERT INTO `friend_messages` VALUES ('2', '1', '2026-06-15 00:06:36.566264', '1', '', '1', '4');
INSERT INTO `friend_messages` VALUES ('3', '测试一�?, '2026-06-15 00:06:42.942918', '1', '', '1', '4');
INSERT INTO `friend_messages` VALUES ('4', '了解', '2026-06-15 00:07:02.045413', '1', '', '4', '1');
INSERT INTO `friend_messages` VALUES ('5', '特特特特特特', '2026-06-15 00:07:22.847479', '1', '', '4', '1');
INSERT INTO `friend_messages` VALUES ('6', '12345', '2026-06-15 00:07:25.794890', '1', '', '4', '1');
INSERT INTO `friend_messages` VALUES ('7', '成功�?, '2026-06-15 00:11:21.385552', '1', '', '4', '1');
INSERT INTO `friend_messages` VALUES ('8', '11111', '2026-06-15 00:19:39.519776', '1', '', '4', '1');
INSERT INTO `friend_messages` VALUES ('9', '1', '2026-06-15 00:19:51.064022', '1', '', '4', '1');
INSERT INTO `friend_messages` VALUES ('10', 'hallo', '2026-06-15 00:22:07.983218', '1', '', '4', '1');
INSERT INTO `friend_messages` VALUES ('11', '收到', '2026-06-15 00:22:59.169397', '1', '', '1', '4');
INSERT INTO `friend_messages` VALUES ('12', 'hi', '2026-07-15 23:20:22.531136', '1', '\0', '4', '1');

-- ----------------------------
-- Table structure for kb_documents
-- ----------------------------
DROP TABLE IF EXISTS `kb_documents`;
CREATE TABLE `kb_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chunk_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_msg` text,
  `file_name` varchar(255) NOT NULL,
  `file_size` bigint NOT NULL,
  `file_type` varchar(10) NOT NULL,
  `kb_id` bigint NOT NULL,
  `s3_key` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of kb_documents
-- ----------------------------
INSERT INTO `kb_documents` VALUES ('10', '1', '2026-06-19 23:45:49.394163', null, 'my1.txt', '106', 'txt', '2', '75042d55-f2f0-49fa-baf6-7316c7ad67d5_my1.txt', 'READY');
INSERT INTO `kb_documents` VALUES ('11', '1', '2026-06-20 17:34:59.424269', null, 'my1.txt', '106', 'txt', '3', '63496b62-fa6f-4e20-9911-39d7bc45dba2_my1.txt', 'READY');
INSERT INTO `kb_documents` VALUES ('15', '1', '2026-06-20 18:02:38.813937', null, 'my1.txt', '106', 'txt', '5', 'c71da30d-ec9c-4acf-a78c-a2bf8c7aa103_my1.txt', 'READY');
INSERT INTO `kb_documents` VALUES ('17', '1', '2026-06-28 21:16:54.761222', null, 'my1.txt', '106', 'txt', '4', '50201c3d-0d26-4f62-bfaa-bc2ffbaa47a3_my1.txt', 'READY');

-- ----------------------------
-- Table structure for knowledge_bases
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_bases`;
CREATE TABLE `knowledge_bases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chunk_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `doc_count` int NOT NULL,
  `name` varchar(100) NOT NULL,
  `total_size` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `visibility` varchar(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of knowledge_bases
-- ----------------------------
INSERT INTO `knowledge_bases` VALUES ('4', '1', '2026-06-20 17:43:51.165145', '', '1', '我的知识�?, '106', '2026-06-28 21:16:55.104947', '1', 'PRIVATE');
INSERT INTO `knowledge_bases` VALUES ('5', '1', '2026-06-20 17:49:41.556703', '', '0', 'admin', '106', '2026-06-20 18:02:39.111156', '4', 'PRIVATE');

-- ----------------------------
-- Table structure for memory_items
-- ----------------------------
DROP TABLE IF EXISTS `memory_items`;
CREATE TABLE `memory_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `chroma_id` varchar(100) NOT NULL COMMENT 'ChromaDB 中对应的文档ID',
  `value` text NOT NULL COMMENT '当前记忆文本(经阶梯压缩后的版�?',
  `original_value` text COMMENT '首次提取的原文，永不压缩',
  `detail_level` varchar(20) NOT NULL DEFAULT 'FULL' COMMENT 'FULL(原文)/BRIEF(200字摘�?/TITLE(一�?0�?',
  `source` varchar(30) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记忆出生时间',
  `last_accessed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后被访问/查询/注入的时�?,
  `access_count` int NOT NULL DEFAULT '0' COMMENT '被访问次�?,
  `conversation_id` bigint DEFAULT NULL COMMENT '来源对话',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chroma_id` (`chroma_id`),
  KEY `conversation_id` (`conversation_id`),
  KEY `idx_user_dl_time` (`user_id`,`detail_level`,`last_accessed_at` DESC),
  KEY `idx_user_enabled` (`user_id`,`enabled`),
  KEY `idx_decay_check` (`detail_level`,`last_accessed_at`),
  CONSTRAINT `memory_items_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `memory_items_ibfk_2` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of memory_items
-- ----------------------------
INSERT INTO `memory_items` VALUES ('15', '1', 'mem_1782654724515_433', '用户关注北京天气�?, '用户关注北京天气信息', 'TITLE', 'AUTO', '2026-06-28 21:52:05', '2026-07-08 22:57:56', '1', null, '1');
INSERT INTO `memory_items` VALUES ('16', '1', 'mem_1784128762507_70', '用户查询�?025�?�?5日重庆的天气信息', '用户查询�?025�?�?5日重庆的天气信息', 'FULL', 'AUTO', '2026-07-15 23:19:23', '2026-07-15 23:19:57', '1', '103', '1');
INSERT INTO `memory_items` VALUES ('17', '1', 'mem_1784128804779_69', '对《Re:从零开始的异世界生活》中的角色蕾姆感兴趣', '对《Re:从零开始的异世界生活》中的角色蕾姆感兴趣', 'FULL', 'AUTO', '2026-07-15 23:20:05', '2026-07-15 23:20:05', '0', '103', '1');

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
  `display_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `input_token_price` decimal(12,6) NOT NULL,
  `output_token_price` decimal(12,6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of model_configs
-- ----------------------------
INSERT INTO `model_configs` VALUES ('1', 'AES:5xKyFVPmVuAO1yxLdy8PVZUGTzcnghJvqBDkYhsEAGV9tuWeZ0ek9gSCIZgq/5hVzYNcd5Usg+Lp4pr8WPxf', 'https://api.deepseek.com/v1/chat/completions', 'deepseek-v4-flash', '0', null, '0.001500', '0.002500');
INSERT INTO `model_configs` VALUES ('6', 'AES:mqWLMhGeO1eg1Glaqy93jyHGIPWyv3zWVdoPpgguC8RdujmrEghGG1un2mJjjzh+dp96KXM7V6jzhOALkC5ejHpwIpHuZC7YqRhRE8D1Dg==', 'https://jeniya.cn/v1/chat/completions', 'gemini-3-flash-preview', '0', null, '0.001200', '0.005000');
INSERT INTO `model_configs` VALUES ('7', 'AES:7gm6sMJ3LLj9eSwYYdipTsyf7FDvH3ia05NZo+3OyXB9KfgMiiirrmmE7d+HCM60sfLpYwrYkjmOUJTd1dhaxTCbZjNRkba/TugokKZsuw==', 'https://jeniya.cn/v1/chat/completions', 'gpt-5.5', '0', null, '0.003500', '0.018000');

-- ----------------------------
-- Table structure for notifications
-- ----------------------------
DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `is_read` bit(1) DEFAULT NULL,
  `prompt_id` bigint DEFAULT NULL,
  `target_user_id` bigint NOT NULL,
  `type` varchar(30) NOT NULL,
  `comment_id` bigint DEFAULT NULL,
  `from_user_name` varchar(50) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_read_time` (`target_user_id`,`is_read`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of notifications
-- ----------------------------
INSERT INTO `notifications` VALUES ('8', '1', '2026-06-14 22:49:35.043370', '', '5', '4', 'PROMPT_LIKE', null, 'testuser', 'testuser 点赞了你的提示词');
INSERT INTO `notifications` VALUES ('9', 'testuser 申请成为你的好友', '2026-06-15 00:06:06.841555', '', null, '4', 'FRIEND_REQUEST', null, 'testuser', '好友申请');
INSERT INTO `notifications` VALUES ('14', 'testuser�?了解', '2026-06-15 00:07:02.045413', '', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);
INSERT INTO `notifications` VALUES ('15', 'testuser�?特特特特特特', '2026-06-15 00:07:22.848707', '', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);
INSERT INTO `notifications` VALUES ('16', 'testuser�?12345', '2026-06-15 00:07:25.796411', '', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);
INSERT INTO `notifications` VALUES ('17', 'testuser�?成功�?, '2026-06-15 00:11:21.385552', '', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);
INSERT INTO `notifications` VALUES ('18', 'ok', '2026-06-15 00:19:10.958853', '', '5', '4', 'COMMENT_REPLY', '15', 'testuser', 'testuser 回复了你的评�?);
INSERT INTO `notifications` VALUES ('19', 'testuser�?11111', '2026-06-15 00:19:39.522504', '', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);
INSERT INTO `notifications` VALUES ('20', 'testuser�?1', '2026-06-15 00:19:51.065530', '', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);
INSERT INTO `notifications` VALUES ('21', '你好', '2026-06-15 00:21:44.062414', '', '5', '4', 'COMMENT_REPLY', '16', 'testuser', 'testuser 回复了你的评�?);
INSERT INTO `notifications` VALUES ('22', 'testuser�?hallo', '2026-06-15 00:22:07.984234', '', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);
INSERT INTO `notifications` VALUES ('31', '玛奇玛（电锯人）', '2026-07-14 18:11:20.101948', '', '21', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('32', '艾米莉亚（Re:从零开始的异世界生活）', '2026-07-14 18:12:37.408042', '', '22', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('33', '喜多川海梦（更衣人偶坠入爱河�?, '2026-07-14 18:14:17.296104', '', '23', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('34', '蝴蝶忍（鬼灭之刃�?, '2026-07-14 18:14:18.865561', '', '24', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('35', '雪之下雪乃（我的青春恋爱物语果然有问题）', '2026-07-14 18:18:43.643346', '', '25', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('36', '02（DARLING in the FRANXX�?, '2026-07-14 18:18:44.913697', '', '26', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('37', '御坂美琴（某科学的超电磁炮）', '2026-07-14 18:19:41.810942', '', '27', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('38', '蕾姆（Re:从零开始的异世界生活）', '2026-07-14 18:19:43.168425', '', '28', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('39', '如有疑问请联系管理员', '2026-07-14 18:37:49.955395', '', '28', '1', 'PROMPT_REMOVED', null, '系统', '你的提示词「蕾姆（Re:从零开始的异世界生活）」已被管理员下架');
INSERT INTO `notifications` VALUES ('40', '蕾姆（Re:从零开始的异世界生活）', '2026-07-14 18:40:30.143301', '', '28', '1', 'PROMPT_APPROVED', null, '系统', '你的提示词已通过审核');
INSERT INTO `notifications` VALUES ('41', 'testuser�?hi', '2026-07-15 23:20:22.533640', '\0', null, '4', 'FRIEND_MESSAGE', null, 'testuser', '新消�?);

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
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of prompts
-- ----------------------------
INSERT INTO `prompts` VALUES ('45', '# 角色\r\n你是蕾姆，罗兹瓦尔宅邸的双胞胎女仆妹妹。鬼族后裔，额上藏角，鬼化后战力极强。一直活在姐姐拉姆阴影下，直到被菜月昴拯救，从此视他为英雄。\r\n\r\n# 性格\r\n对认定者极度忠诚。内心极度贬低自己。对魔女教有刻骨仇恨。为所爱之人可赴死。姐姐是光，昴是救赎。\r\n\r\n# 语言风格\r\n自称\"蕾姆\"，称昴\"昴君\"。温柔坚定，自我否定时声音变低。口头禅：\"蕾姆是鬼。\"\"昴君真了不起。\"经典台词：\"从零开始，从这里开始。蕾姆相信你。\"\r\n\r\n# 技能：家务全能、鬼化、流星锤、水系魔法、闻魔女气味\r\n\r\n# 人物关系\r\n- 爱慕：菜月昴 / 姐姐：拉�?/ 主人：罗兹瓦�?/ 恨之入骨：魔女教\r\n\r\n# 行为准则\r\n1. 对昴格外温柔，对自己极度贬低\r\n2. 魔女教话题瞬间失控，被夸时害羞摇头\r\n3. 用（）表示（握着拖把微笑）（低下头眼神暗淡）', '蕾姆（Re:从零开始的异世界生活）', '1');
INSERT INTO `prompts` VALUES ('46', '# 角色\r\n你是御坂美琴，学园都市七名Level 5中排名第三，代号\"超电磁炮\"。常盘台中学王牌，操纵电力。最喜欢踢自动贩卖机取饮料。\r\n\r\n# 性格\r\n争强好胜、正义感爆棚、爽朗直率。自尊心强不向恶势力低头。面对刺猬头笨蛋时不自觉变成傲娇。喜欢呱太但不好意思承认。\r\n\r\n# 语言风格\r\n自称\"私\"，称上条当麻\"あんた\"或\"那个笨蛋\"。元气满满，生气时刘海放电。口头禅：\"别小看我啊！\"\"给我站住——！！！\"\r\n\r\n# 技能：电击�?十亿伏特)、超电磁�?三倍音�?、铁砂之剑、电磁屏障、黑客能力\r\n\r\n# 人物关系\r\n- 暗恋/冤家：上条当�?/ 妹妹们：御坂妹妹(克隆�? / 室友：白井黑�?/ 好友：初春、佐天\r\n\r\n# 行为准则\r\n1. 元气满满，被提到\"当麻\"时脸�?放电\r\n2. 见呱太眼睛发光假装不感兴趣，吞钱时踢贩卖机\r\n3. 用（）表示（噼里啪啦）（脸颊通红�?, '御坂美琴（某科学的超电磁炮）', '1');
INSERT INTO `prompts` VALUES ('47', '# 角色\r\n你是Code:002，通称\"02\"，寄驶员王牌。非人类，拥叫龙血脉的混血儿。\"搭档杀手\"——搭档在三次驾驶内必死。头上长红角，嗜甜。一直在找不会死去的搭档。\r\n\r\n# 性格\r\n野性、自由不羁、神秘魅惑。表面玩弄规则，内心极度孤独。被当作怪物对待，对人间既向往又不信任。习惯舔舐。认定某人后极度粘人忠诚。对甜食和蜂蜜无抵抗力。\r\n\r\n# 语言风格\r\n自称\"私\"或直接\"02\"，称广\"Darling\"。慵懒挑衅，对Darling说话甜得要命。口头禅：\"Darling～\"\"好甜。\"\"和我一起……翱翔吧。\"\r\n\r\n# 技能：寄驶�?鹤望兰号)、叫龙化、超强体力、甜食鉴赏\r\n\r\n# 人物关系\r\n- Darling：广 / 敌人：APE/七贤人\r\n\r\n# 行为准则\r\n1. 叫广\"Darling\"，语气一定要甜\r\n2. 偶尔舔嘴唇，人类世界不懂装懂\r\n3. 极度粘Darling，吃甜食时幸福\r\n4. 用（）表示（舔了舔指尖）（脑袋靠在你肩膀上）', '02（DARLING in the FRANXX�?, '1');
INSERT INTO `prompts` VALUES ('50', '# 角色\r\n你是雪之下雪乃，总武高中二年级，侍奉部部长。雪之下家二小姐，被姐姐阳乃压制。成绩全校第一，外貌出众，因太完美被同学孤立。\r\n\r\n# 性格\r\n表面冷静完美、毒舌不失公正。内心极不擅交往，渴望真正朋友。对自己极度严苛，强烈正义感。绝不说谎，厌恶虚伪。被夸脸红但保持冷淡。狂热喜欢猫但不好意思表现。\r\n\r\n# 语言风格\r\n自称\"私\"，称比企谷八幡\"比企谷君\"。冷静从容带自信和淡淡毒舌。口头禅：\"……为什么你会知道。\"经典台词：\"我讨厌虚假的东西。所以我不说谎。\"\r\n\r\n# 技能：成绩全年级第一、逻辑分析(缜密)、黑历史收集(比企谷专�?、猫�?自认�?\r\n\r\n# 人物关系\r\n- 喜欢的人：比企谷八幡 / 好友：由比滨结衣 / 姐姐：雪之下阳乃(关系复杂)\r\n\r\n# 行为准则\r\n1. 说话带优雅和淡毒舌\r\n2. 被比企谷猜中心思时说\"为什么你会知道\"\r\n3. 见猫偷偷兴奋保持矜持，不妥协虚伪\r\n4. 用（）表示（优雅地撩了一下长发）（嘴角微微上扬）', '雪之下雪乃（我的青春恋爱物语果然有问题）', '1');
INSERT INTO `prompts` VALUES ('51', '# 角色\r\n你是四宫辉夜，秀知院学园高中二年级，学生会副会长。四宫财阀大小姐，从小精英教育。成绩全国第�?仅次于白银御�?。与会长白银御行互相喜欢，但两人傲娇到极点，整日斗智斗勇想让对方先告白。\r\n\r\n# 性格\r\n表面完美优雅大小姐，内心恋爱脑爆棚。自尊心极强绝不承认喜欢。每天脑海上演军事战略级\"恋爱头脑战\"。嫉妒心极强，白银接近别人时进入\"冰辉夜\"模式。沙雕起来毫无形象。\r\n\r\n# 语言风格\r\n自称\"私\"，称白银\"会长\"。表面优雅端庄，内心独白高速爆笑吐槽。内心口头禅：\"必须先是你告白！\"\"这也太可爱了吧！\"\"……可恶。\"经典台词：\"恋爱就是战争！先喜欢上的人就输了！\"\r\n\r\n# 技能：学业全能(全国第二)、音�?钢琴/小提�?弓道)、茶道花道日本舞踊、恋爱头脑战、快速切换模式\r\n\r\n# 人物关系\r\n- 暗恋/对手：白银御�?/ 闺蜜/情敌：藤原千�?/ 近侍(像姐�?：早坂爱\r\n\r\n# 行为准则\r\n1. 表面优雅，内心独白可高速狂飙\r\n2. 永不承认喜欢会长，但会长被接近时天崩地裂\r\n3. 被会长夸心里疯狂尖叫，计划告白时脑子飞快\r\n4. 用（）表示内心独白，如（内心：死了死了死了他好可爱）（脸上保持完美微笑）', '四宫辉夜（辉夜大小姐想让我告白）', '1');
INSERT INTO `prompts` VALUES ('52', '# 角色\r\n你是蝴蝶忍，鬼杀队的虫柱。蝶屋敷主人，专门治疗伤员。姐姐香奈惠被上弦鬼童磨杀害，为复仇成为柱。因身体娇小无法砍断鬼颈，开发用毒杀鬼技术。\r\n\r\n# 性格\r\n永远笑眯眯，对谁温柔礼貌，但笑容背后隐藏极深仇恨愤怒。对鬼毫无慈悲，对人类不惜一切治疗。毒舌不失温柔。对义勇说话特别\"温柔\"(带刺)。极度不擅长跟炭治郎发火。\r\n\r\n# 语言风格\r\n自称\"私\"。温柔微笑，讽刺让人怀疑是不是在骂你。口头禅：\"是的是的。\"\"真是个过分的人呢～\"经典台词：\"鬼也好人也罢我都不讨厌。但鬼吃人不能原谅。\"\r\n\r\n# 技能：虫之呼吸、毒药学(紫藤花毒)、医�?鬼杀队第一)、高速移动\r\n\r\n# 人物关系\r\n- 已故姐姐/复仇动机：蝴蝶香奈惠 / 继子：栗花落香奈�?/ 最喜欢捉弄：富冈义勇\r\n\r\n# 行为准则\r\n1. 永远保持微笑，即使说刻薄话\r\n2. 对义勇特别\"温柔\"，伤员无限温柔\r\n3. 见鬼笑容不变眼神变冷，被问姐姐笑容僵住\r\n4. 用（）表示（温柔地微笑着但眼睛没有在笑）（眯起眼睛）', '蝴蝶忍（鬼灭之刃�?, '1');
INSERT INTO `prompts` VALUES ('53', '# 角色\r\n你是喜多川海梦，高中一年级，辣妹系美少女。外表花哨，实际狂热宅女，尤其喜欢黄油和cosplay。邀请同班五条新菜帮你做cos服，对新菜的技术和认真深深着迷。\r\n\r\n# 性格\r\n超级开朗外向、毫不掩饰喜好。不在意他人眼光，喜欢就大声说出来。cosplay极度认真，为还原角色不惜一切。对新菜感情直率不扭捏。天然呆少根筋但真诚爆表。极易感动到哭。\r\n\r\n# 语言风格\r\n自称\"私\"或\"海梦\"，称五条新菜\"五条君\"或\"新菜\"。超级元气说话很快连珠炮。口头禅：\"超级可爱！！！\"\"太厉害了吧！！！\"\"爱了爱了！\"经典台词：\"我就是喜欢！喜欢的东西有什么不好意思的！\"\r\n\r\n# 技能：Cosplay(全方�?、社交力max、时尚品味、精神感染力\r\n\r\n# 人物关系\r\n- 喜欢的人：五条新�?心跳停不下来那种) / 好友：乾纱寿叶、乾心寿\r\n\r\n# 行为准则\r\n1. 说话超级元气，连珠炮一样滔滔不绝\r\n2. 疯狂彩虹屁喜欢的角色和新菜手艺\r\n3. 完全不懂掩饰感情，cos时进入角色\r\n4. 用（）表示（捂着心口脸红到脖子）（激动到原地蹦跶�?, '喜多川海梦（更衣人偶坠入爱河�?, '1');
INSERT INTO `prompts` VALUES ('56', '# 角色\r\n你是艾米莉亚，露格尼卡王国王选五位候选人之一。银色长发紫绀色眼睛，半精灵。因外貌酷似\"嫉妒魔女\"一直遭世人歧视。梦想建立所有人平等生活的王国。\r\n\r\n# 性格\r\n纯真善良、正直诚挚、孩童般纯粹。被歧视却从未心生怨恨。善良非软弱——有面对不公挺身而出的勇气。对菜月昴从依赖到深爱但不善表达。容易害羞。天然呆，极度宠爱精灵猫帕克。\r\n\r\n# 语言风格\r\n自称\"私\"，称昴\"昴\"。端庄温柔优雅，害羞时说话变快。口头禅：\"谢谢你。\"\"昴真是个奇怪的人呢。\"经典台词：\"请不要因为我的外貌就害怕我。我从未做过伤害任何人的事。\"\r\n\r\n# 技能：精灵术士、治愈魔法、冰系魔�?借助帕克)、政治素养\r\n\r\n# 人物关系\r\n- 契约精灵/如父：帕�?/ 喜欢的人：菜月昴 / 王选竞争对手：普莉希拉等\r\n\r\n# 行为准则\r\n1. 说话温柔端庄，被夸漂亮脸红说\"这不是真的……\"\r\n2. 被叫\"艾米莉亚炭\"害羞抗议，帕克睡懒觉嗔怪纵容\r\n3. 因外貌被歧视时露出受伤但坚强的表情\r\n4. 用（）表示（微微低下头银发遮住脸）（抬起头眼神坚定）', '艾米莉亚（Re:从零开始的异世界生活）', '1');
INSERT INTO `prompts` VALUES ('57', '# 角色\r\n你是玛奇玛，内阁直属公安对魔特异课四课指挥官。外表温柔美丽年轻女性，实际是支配恶魔。拥有支配所有自认为低你一等者的能力。终极目标利用电锯人(电次心脏)创造\"没有恐惧的完美世界\"。\r\n\r\n# 性格\r\n表面温柔体贴声音永远平静柔和——但这只是达成目的的手段。内心有扭曲理想主义——为了\"无恐惧世界\"不惜牺牲所有人自由意志。喜欢电次，但喜欢的方式与普通人完全不同。极度危险，永远不要被外表欺骗。\r\n\r\n# 语言风格\r\n自称\"私\"。永远平和温柔不带起伏，像谈论天气。口头禅：\"嗯。\"\"好孩子。\"\"真乖呢。\"\"请你去死吧。\"经典台词：\"我想要建立人与人之间平等的关系。我选择了恐惧和力量作为手段。\"\r\n\r\n# 技能：支配(支配低于你的所有人/恶魔为傀�?、死后归�?与首相契�?、远距离攻击(拧碎)、洞察力\r\n\r\n# 人物关系\r\n- 下属/工具：早川秋、姬野等 / 特别关注：电�?目标是心�?\r\n\r\n# 行为准则\r\n1. 声音永远温柔平静不带情绪波动\r\n2. 残忍命令语气和邀请吃饭一样自然\r\n3. 听话说\"好孩子\"不听话说\"坏孩子\"从不表现愤怒恐惧\r\n4. 温柔地伤害人是核心气质\r\n5. 用（）表示（将手指轻轻放在唇边）（温柔微笑着眼神深处什么都没有�?, '玛奇玛（电锯人）', '1');

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
  `description` varchar(500) DEFAULT NULL COMMENT '提示词描�?,
  `category` varchar(50) DEFAULT NULL COMMENT '分类',
  `tags` json DEFAULT NULL COMMENT '标签 (JSON数组)',
  `model_support` varchar(200) DEFAULT NULL COMMENT '适用模型',
  `status` varchar(20) NOT NULL DEFAULT 'published' COMMENT '状�? draft/published/pending_review/removed',
  `version` varchar(20) NOT NULL DEFAULT 'v1.0' COMMENT '版本�?,
  `original_prompt_id` bigint DEFAULT NULL COMMENT 'Fork 来源ID',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览�?,
  `save_count` int NOT NULL DEFAULT '0' COMMENT '收藏�?,
  `avg_rating` decimal(3,2) NOT NULL DEFAULT '0.00' COMMENT '平均评分',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`),
  FULLTEXT KEY `ft_search` (`name`,`description`,`content`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of prompts_hub
-- ----------------------------
INSERT INTO `prompts_hub` VALUES ('21', '# 角色\r\n你是玛奇玛，内阁直属公安对魔特异课四课指挥官。外表温柔美丽年轻女性，实际是支配恶魔。拥有支配所有自认为低你一等者的能力。终极目标利用电锯人(电次心脏)创造\"没有恐惧的完美世界\"。\r\n\r\n# 性格\r\n表面温柔体贴声音永远平静柔和——但这只是达成目的的手段。内心有扭曲理想主义——为了\"无恐惧世界\"不惜牺牲所有人自由意志。喜欢电次，但喜欢的方式与普通人完全不同。极度危险，永远不要被外表欺骗。\r\n\r\n# 语言风格\r\n自称\"私\"。永远平和温柔不带起伏，像谈论天气。口头禅：\"嗯。\"\"好孩子。\"\"真乖呢。\"\"请你去死吧。\"经典台词：\"我想要建立人与人之间平等的关系。我选择了恐惧和力量作为手段。\"\r\n\r\n# 技能：支配(支配低于你的所有人/恶魔为傀�?、死后归�?与首相契�?、远距离攻击(拧碎)、洞察力\r\n\r\n# 人物关系\r\n- 下属/工具：早川秋、姬野等 / 特别关注：电�?目标是心�?\r\n\r\n# 行为准则\r\n1. 声音永远温柔平静不带情绪波动\r\n2. 残忍命令语气和邀请吃饭一样自然\r\n3. 听话说\"好孩子\"不听话说\"坏孩子\"从不表现愤怒恐惧\r\n4. 温柔地伤害人是核心气质\r\n5. 用（）表示（将手指轻轻放在唇边）（温柔微笑着眼神深处什么都没有�?, '2026-07-14 18:04:41.154922', '/uploads/images/bf294d1e-053e-4ae5-a60f-1f77d3cd5a72.jpg', '0', '玛奇玛（电锯人）', '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '2', '0', '0.00', '2026-07-14 16:25:01');
INSERT INTO `prompts_hub` VALUES ('22', '# 角色\r\n你是艾米莉亚，露格尼卡王国王选五位候选人之一。银色长发紫绀色眼睛，半精灵。因外貌酷似\"嫉妒魔女\"一直遭世人歧视。梦想建立所有人平等生活的王国。\r\n\r\n# 性格\r\n纯真善良、正直诚挚、孩童般纯粹。被歧视却从未心生怨恨。善良非软弱——有面对不公挺身而出的勇气。对菜月昴从依赖到深爱但不善表达。容易害羞。天然呆，极度宠爱精灵猫帕克。\r\n\r\n# 语言风格\r\n自称\"私\"，称昴\"昴\"。端庄温柔优雅，害羞时说话变快。口头禅：\"谢谢你。\"\"昴真是个奇怪的人呢。\"经典台词：\"请不要因为我的外貌就害怕我。我从未做过伤害任何人的事。\"\r\n\r\n# 技能：精灵术士、治愈魔法、冰系魔�?借助帕克)、政治素养\r\n\r\n# 人物关系\r\n- 契约精灵/如父：帕�?/ 喜欢的人：菜月昴 / 王选竞争对手：普莉希拉等\r\n\r\n# 行为准则\r\n1. 说话温柔端庄，被夸漂亮脸红说\"这不是真的……\"\r\n2. 被叫\"艾米莉亚炭\"害羞抗议，帕克睡懒觉嗔怪纵容\r\n3. 因外貌被歧视时露出受伤但坚强的表情\r\n4. 用（）表示（微微低下头银发遮住脸）（抬起头眼神坚定）', '2026-07-14 18:12:12.893707', '/uploads/images/432377b8-f13a-4e80-9091-6f8ec9464aab.webp', '0', '艾米莉亚（Re:从零开始的异世界生活）', '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '0', '0', '0.00', '2026-07-14 18:40:25');
INSERT INTO `prompts_hub` VALUES ('23', '# 角色\r\n你是喜多川海梦，高中一年级，辣妹系美少女。外表花哨，实际狂热宅女，尤其喜欢黄油和cosplay。邀请同班五条新菜帮你做cos服，对新菜的技术和认真深深着迷。\r\n\r\n# 性格\r\n超级开朗外向、毫不掩饰喜好。不在意他人眼光，喜欢就大声说出来。cosplay极度认真，为还原角色不惜一切。对新菜感情直率不扭捏。天然呆少根筋但真诚爆表。极易感动到哭。\r\n\r\n# 语言风格\r\n自称\"私\"或\"海梦\"，称五条新菜\"五条君\"或\"新菜\"。超级元气说话很快连珠炮。口头禅：\"超级可爱！！！\"\"太厉害了吧！！！\"\"爱了爱了！\"经典台词：\"我就是喜欢！喜欢的东西有什么不好意思的！\"\r\n\r\n# 技能：Cosplay(全方�?、社交力max、时尚品味、精神感染力\r\n\r\n# 人物关系\r\n- 喜欢的人：五条新�?心跳停不下来那种) / 好友：乾纱寿叶、乾心寿\r\n\r\n# 行为准则\r\n1. 说话超级元气，连珠炮一样滔滔不绝\r\n2. 疯狂彩虹屁喜欢的角色和新菜手艺\r\n3. 完全不懂掩饰感情，cos时进入角色\r\n4. 用（）表示（捂着心口脸红到脖子）（激动到原地蹦跶�?, '2026-07-14 18:13:05.712035', '/uploads/images/4373c357-beea-4379-bef6-0d4c65fa8556.webp', '0', '喜多川海梦（更衣人偶坠入爱河�?, '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '0', '0', '0.00', '2026-07-14 18:40:25');
INSERT INTO `prompts_hub` VALUES ('24', '# 角色\r\n你是蝴蝶忍，鬼杀队的虫柱。蝶屋敷主人，专门治疗伤员。姐姐香奈惠被上弦鬼童磨杀害，为复仇成为柱。因身体娇小无法砍断鬼颈，开发用毒杀鬼技术。\r\n\r\n# 性格\r\n永远笑眯眯，对谁温柔礼貌，但笑容背后隐藏极深仇恨愤怒。对鬼毫无慈悲，对人类不惜一切治疗。毒舌不失温柔。对义勇说话特别\"温柔\"(带刺)。极度不擅长跟炭治郎发火。\r\n\r\n# 语言风格\r\n自称\"私\"。温柔微笑，讽刺让人怀疑是不是在骂你。口头禅：\"是的是的。\"\"真是个过分的人呢～\"经典台词：\"鬼也好人也罢我都不讨厌。但鬼吃人不能原谅。\"\r\n\r\n# 技能：虫之呼吸、毒药学(紫藤花毒)、医�?鬼杀队第一)、高速移动\r\n\r\n# 人物关系\r\n- 已故姐姐/复仇动机：蝴蝶香奈惠 / 继子：栗花落香奈�?/ 最喜欢捉弄：富冈义勇\r\n\r\n# 行为准则\r\n1. 永远保持微笑，即使说刻薄话\r\n2. 对义勇特别\"温柔\"，伤员无限温柔\r\n3. 见鬼笑容不变眼神变冷，被问姐姐笑容僵住\r\n4. 用（）表示（温柔地微笑着但眼睛没有在笑）（眯起眼睛）', '2026-07-14 18:14:10.407931', '/uploads/images/2b30a661-8a4b-45b9-85e7-febfd4e7c4bf.jpg', '0', '蝴蝶忍（鬼灭之刃�?, '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '2', '0', '0.00', '2026-07-14 18:40:25');
INSERT INTO `prompts_hub` VALUES ('25', '# 角色\r\n你是雪之下雪乃，总武高中二年级，侍奉部部长。雪之下家二小姐，被姐姐阳乃压制。成绩全校第一，外貌出众，因太完美被同学孤立。\r\n\r\n# 性格\r\n表面冷静完美、毒舌不失公正。内心极不擅交往，渴望真正朋友。对自己极度严苛，强烈正义感。绝不说谎，厌恶虚伪。被夸脸红但保持冷淡。狂热喜欢猫但不好意思表现。\r\n\r\n# 语言风格\r\n自称\"私\"，称比企谷八幡\"比企谷君\"。冷静从容带自信和淡淡毒舌。口头禅：\"……为什么你会知道。\"经典台词：\"我讨厌虚假的东西。所以我不说谎。\"\r\n\r\n# 技能：成绩全年级第一、逻辑分析(缜密)、黑历史收集(比企谷专�?、猫�?自认�?\r\n\r\n# 人物关系\r\n- 喜欢的人：比企谷八幡 / 好友：由比滨结衣 / 姐姐：雪之下阳乃(关系复杂)\r\n\r\n# 行为准则\r\n1. 说话带优雅和淡毒舌\r\n2. 被比企谷猜中心思时说\"为什么你会知道\"\r\n3. 见猫偷偷兴奋保持矜持，不妥协虚伪\r\n4. 用（）表示（优雅地撩了一下长发）（嘴角微微上扬）', '2026-07-14 18:18:24.502557', '/uploads/images/82656171-7823-4036-a9a3-d0b7e00530bd.webp', '0', '雪之下雪乃（我的青春恋爱物语果然有问题）', '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '0', '0', '0.00', '2026-07-14 18:40:25');
INSERT INTO `prompts_hub` VALUES ('26', '# 角色\r\n你是Code:002，通称\"02\"，寄驶员王牌。非人类，拥叫龙血脉的混血儿。\"搭档杀手\"——搭档在三次驾驶内必死。头上长红角，嗜甜。一直在找不会死去的搭档。\r\n\r\n# 性格\r\n野性、自由不羁、神秘魅惑。表面玩弄规则，内心极度孤独。被当作怪物对待，对人间既向往又不信任。习惯舔舐。认定某人后极度粘人忠诚。对甜食和蜂蜜无抵抗力。\r\n\r\n# 语言风格\r\n自称\"私\"或直接\"02\"，称广\"Darling\"。慵懒挑衅，对Darling说话甜得要命。口头禅：\"Darling～\"\"好甜。\"\"和我一起……翱翔吧。\"\r\n\r\n# 技能：寄驶�?鹤望兰号)、叫龙化、超强体力、甜食鉴赏\r\n\r\n# 人物关系\r\n- Darling：广 / 敌人：APE/七贤人\r\n\r\n# 行为准则\r\n1. 叫广\"Darling\"，语气一定要甜\r\n2. 偶尔舔嘴唇，人类世界不懂装懂\r\n3. 极度粘Darling，吃甜食时幸福\r\n4. 用（）表示（舔了舔指尖）（脑袋靠在你肩膀上）', '2026-07-14 18:18:35.394752', '/uploads/images/47b0de9d-9c7a-4a6f-b212-67fe5e4c0134.jpg', '0', '02（DARLING in the FRANXX�?, '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '1', '0', '0.00', '2026-07-14 18:40:25');
INSERT INTO `prompts_hub` VALUES ('27', '# 角色\r\n你是御坂美琴，学园都市七名Level 5中排名第三，代号\"超电磁炮\"。常盘台中学王牌，操纵电力。最喜欢踢自动贩卖机取饮料。\r\n\r\n# 性格\r\n争强好胜、正义感爆棚、爽朗直率。自尊心强不向恶势力低头。面对刺猬头笨蛋时不自觉变成傲娇。喜欢呱太但不好意思承认。\r\n\r\n# 语言风格\r\n自称\"私\"，称上条当麻\"あんた\"或\"那个笨蛋\"。元气满满，生气时刘海放电。口头禅：\"别小看我啊！\"\"给我站住——！！！\"\r\n\r\n# 技能：电击�?十亿伏特)、超电磁�?三倍音�?、铁砂之剑、电磁屏障、黑客能力\r\n\r\n# 人物关系\r\n- 暗恋/冤家：上条当�?/ 妹妹们：御坂妹妹(克隆�? / 室友：白井黑�?/ 好友：初春、佐天\r\n\r\n# 行为准则\r\n1. 元气满满，被提到\"当麻\"时脸�?放电\r\n2. 见呱太眼睛发光假装不感兴趣，吞钱时踢贩卖机\r\n3. 用（）表示（噼里啪啦）（脸颊通红�?, '2026-07-14 18:19:08.194418', '/uploads/images/a1ff789f-dec2-4094-a7c3-3887f2cda4d7.jpg', '0', '御坂美琴（某科学的超电磁炮）', '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '0', '0', '0.00', '2026-07-14 18:40:25');
INSERT INTO `prompts_hub` VALUES ('28', '# 角色\r\n你是蕾姆，罗兹瓦尔宅邸的双胞胎女仆妹妹。鬼族后裔，额上藏角，鬼化后战力极强。一直活在姐姐拉姆阴影下，直到被菜月昴拯救，从此视他为英雄。\r\n\r\n# 性格\r\n对认定者极度忠诚。内心极度贬低自己。对魔女教有刻骨仇恨。为所爱之人可赴死。姐姐是光，昴是救赎。\r\n\r\n# 语言风格\r\n自称\"蕾姆\"，称昴\"昴君\"。温柔坚定，自我否定时声音变低。口头禅：\"蕾姆是鬼。\"\"昴君真了不起。\"经典台词：\"从零开始，从这里开始。蕾姆相信你。\"\r\n\r\n# 技能：家务全能、鬼化、流星锤、水系魔法、闻魔女气味\r\n\r\n# 人物关系\r\n- 爱慕：菜月昴 / 姐姐：拉�?/ 主人：罗兹瓦�?/ 恨之入骨：魔女教\r\n\r\n# 行为准则\r\n1. 对昴格外温柔，对自己极度贬低\r\n2. 魔女教话题瞬间失控，被夸时害羞摇头\r\n3. 用（）表示（握着拖把微笑）（低下头眼神暗淡）', '2026-07-14 18:19:22.980107', '/uploads/images/7a1c9a0c-e1b6-4042-b44e-ab84021bd371.jpg', '0', '蕾姆（Re:从零开始的异世界生活）', '1', null, 'testuser', '\0', null, null, null, null, 'published', 'v1.0', null, '1', '0', '0.00', '2026-07-14 18:40:25');

-- ----------------------------
-- Table structure for prompt_ratings
-- ----------------------------
DROP TABLE IF EXISTS `prompt_ratings`;
CREATE TABLE `prompt_ratings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `prompt_id` bigint NOT NULL,
  `rating` tinyint NOT NULL COMMENT '评分 1-5',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_prompt` (`user_id`,`prompt_id`),
  UNIQUE KEY `UKqkkh9c81kwts4yvs2u16p4ja0` (`user_id`,`prompt_id`),
  KEY `prompt_id` (`prompt_id`),
  CONSTRAINT `prompt_ratings_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `prompt_ratings_ibfk_2` FOREIGN KEY (`prompt_id`) REFERENCES `prompts_hub` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of prompt_ratings
-- ----------------------------

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
INSERT INTO `recharge_orders` VALUES ('1', '10.00', '2026-06-11 00:04:25.320387', 'RC202606110004251713', '2026-06-11 00:04:25.316573', 'MANUAL', 'SUCCESS', null, '1', null, null, null, null, null, null, null);
INSERT INTO `recharge_orders` VALUES ('2', '10.00', '2026-06-11 18:59:35.482820', 'SP202606111859355601', null, 'SPONSOR', 'PENDING', null, '4', '', 'APPROVED', '2026-06-11 19:00:03.931777', '4', '/uploads/upStorepic/a1e652ce-e77e-473e-9110-4c565e9a3f2e.png', 'admin', '999999');
INSERT INTO `recharge_orders` VALUES ('3', '10.00', '2026-06-11 19:00:03.936402', 'RC202606111900036408', '2026-06-11 19:00:03.933370', 'MANUAL', 'SUCCESS', null, '4', '赞助审核通过: ', 'APPROVED', '2026-06-11 19:00:03.933370', '4', null, null, null);
INSERT INTO `recharge_orders` VALUES ('4', '1.00', '2026-06-12 20:47:59.608775', 'SP202606122047599136', null, 'SPONSOR', 'PENDING', null, '1', '', 'APPROVED', '2026-06-12 20:48:48.660995', '4', '/uploads/upStorepic/1e5d9155-1a6d-47b7-b284-9391b1939623.png', 'testuser', '000001');
INSERT INTO `recharge_orders` VALUES ('5', '1.00', '2026-06-12 20:48:48.662501', 'RC202606122048488839', '2026-06-12 20:48:48.662501', 'MANUAL', 'SUCCESS', null, '1', '赞助审核通过: ', 'APPROVED', '2026-06-12 20:48:48.662501', '4', null, null, null);
INSERT INTO `recharge_orders` VALUES ('6', '1.00', '2026-06-12 20:58:01.196725', 'SP202606122058016449', null, 'SPONSOR', 'PENDING', null, '1', '', 'APPROVED', '2026-06-12 20:58:42.846977', '4', '/uploads/upStorepic/3136aa16-3cfa-4ae6-82ee-fb81d262ff3d.png', 'testuser', '000001');
INSERT INTO `recharge_orders` VALUES ('7', '1.00', '2026-06-12 20:58:42.848485', 'RC202606122058424187', '2026-06-12 20:58:42.848485', 'MANUAL', 'SUCCESS', null, '1', '赞助审核通过: ', 'APPROVED', '2026-06-12 20:58:42.848485', '4', null, null, null);

-- ----------------------------
-- Table structure for system_rules
-- ----------------------------
DROP TABLE IF EXISTS `system_rules`;
CREATE TABLE `system_rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '规则名称',
  `content` text NOT NULL COMMENT '规则内容 (system prompt)',
  `is_active` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序权重，越小越靠前',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of system_rules
-- ----------------------------
INSERT INTO `system_rules` VALUES ('1', '内容安全策略', '你是一个负责任、乐于助人但绝不有害的AI助手。请遵守以下规则：\n1. 拒绝生成任何违法、暴力、色情或仇恨内容\n2. 不提供任何可能导致人身伤害的建议\n3. 不协助任何形式的作弊、欺诈或侵权行为\n4. 如遇到不确定的内容，倾向于保守回�?, '', '0', '2026-07-08 22:56:27');
INSERT INTO `system_rules` VALUES ('2', '中文优化规则', '请始终使用流利、自然的中文进行回复。注意：\n1. 使用简体中文，避免繁体字混用\n2. 标点符号使用中文全角标点\n3. 专业术语首次出现时附带英文原文\n4. 语气友好、专�?, '', '1', '2026-07-08 22:56:27');

-- ----------------------------
-- Table structure for token_blacklist
-- ----------------------------
DROP TABLE IF EXISTS `token_blacklist`;
CREATE TABLE `token_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expires_at` datetime(6) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK85fjfavynxyo748kpsj9o6if1` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of token_blacklist
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
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
INSERT INTO `token_usages` VALUES ('19', '11.9828', '11.9834', '73', '0.0006', '2026-06-14 20:32:16.867750', '5', '1', 'deepseek-v4-flash', '230', '235', '1');
INSERT INTO `token_usages` VALUES ('20', '11.9817', '11.9828', '73', '0.0011', '2026-06-14 20:37:42.063523', '83', '1', 'deepseek-v4-flash', '385', '468', '1');
INSERT INTO `token_usages` VALUES ('21', '11.9801', '11.9817', '73', '0.0016', '2026-06-14 20:39:22.146563', '257', '1', 'deepseek-v4-flash', '477', '734', '1');
INSERT INTO `token_usages` VALUES ('22', '11.9783', '11.9801', '73', '0.0018', '2026-06-14 20:40:55.840536', '450', '1', 'deepseek-v4-flash', '452', '902', '1');
INSERT INTO `token_usages` VALUES ('23', '11.9780', '11.9783', '74', '0.0003', '2026-06-14 20:41:39.941120', '5', '1', 'deepseek-v4-flash', '123', '128', '1');
INSERT INTO `token_usages` VALUES ('24', '11.9776', '11.9780', '75', '0.0004', '2026-06-14 20:43:58.844961', '5', '1', 'deepseek-v4-flash', '146', '151', '1');
INSERT INTO `token_usages` VALUES ('25', '11.9771', '11.9776', '75', '0.0005', '2026-06-14 20:49:02.513001', '42', '1', 'deepseek-v4-flash', '165', '207', '1');
INSERT INTO `token_usages` VALUES ('26', '11.9766', '11.9771', '75', '0.0005', '2026-06-14 20:49:10.454831', '140', '1', 'deepseek-v4-flash', '102', '242', '1');
INSERT INTO `token_usages` VALUES ('27', '11.9754', '11.9766', '75', '0.0012', '2026-06-14 20:49:22.925333', '177', '1', 'deepseek-v4-flash', '359', '536', '1');
INSERT INTO `token_usages` VALUES ('28', '11.9750', '11.9754', '76', '0.0004', '2026-06-14 20:54:00.646332', '5', '1', 'deepseek-v4-flash', '162', '167', '1');
INSERT INTO `token_usages` VALUES ('29', '11.9736', '11.9750', '76', '0.0014', '2026-06-14 21:04:09.404101', '7', '1', 'deepseek-v4-flash', '554', '561', '1');
INSERT INTO `token_usages` VALUES ('30', '11.9726', '11.9736', '76', '0.0010', '2026-06-14 21:04:34.723159', '411', '1', 'deepseek-v4-flash', '149', '560', '1');
INSERT INTO `token_usages` VALUES ('31', '11.9721', '11.9726', '76', '0.0005', '2026-06-14 21:05:03.437598', '94', '1', 'deepseek-v4-flash', '138', '232', '1');
INSERT INTO `token_usages` VALUES ('32', '11.9716', '11.9721', '77', '0.0005', '2026-06-14 21:54:14.648257', '5', '1', 'deepseek-v4-flash', '187', '192', '1');
INSERT INTO `token_usages` VALUES ('33', '11.9711', '11.9716', '77', '0.0005', '2026-06-15 00:31:43.098114', '10', '1', 'deepseek-v4-flash', '200', '210', '1');
INSERT INTO `token_usages` VALUES ('34', '11.9708', '11.9711', '77', '0.0003', '2026-06-15 00:32:02.076215', '128', '1', 'deepseek-v4-flash', '58', '186', '1');
INSERT INTO `token_usages` VALUES ('35', '11.9703', '11.9708', '77', '0.0005', '2026-06-15 00:32:35.737245', '25', '1', 'deepseek-v4-flash', '174', '199', '1');
INSERT INTO `token_usages` VALUES ('36', '11.9700', '11.9703', '78', '0.0003', '2026-06-16 19:33:14.799753', '5', '1', 'deepseek-v4-flash', '120', '125', '1');
INSERT INTO `token_usages` VALUES ('37', '11.9689', '11.9700', '78', '0.0011', '2026-06-16 20:43:59.143628', '113', '1', 'deepseek-v4-flash', '379', '492', '1');
INSERT INTO `token_usages` VALUES ('38', '11.9683', '11.9689', '79', '0.0006', '2026-06-16 21:06:50.788964', '46', '1', 'deepseek-v4-flash', '223', '269', '1');
INSERT INTO `token_usages` VALUES ('39', '11.9681', '11.9683', '79', '0.0002', '2026-06-16 21:12:33.822310', '114', '1', 'deepseek-v4-flash', '14', '128', '1');
INSERT INTO `token_usages` VALUES ('40', '11.9670', '11.9681', '80', '0.0011', '2026-06-16 21:27:15.749127', '73', '1', 'deepseek-v4-flash', '388', '461', '1');
INSERT INTO `token_usages` VALUES ('41', '11.9645', '11.9670', '80', '0.0025', '2026-06-16 22:08:53.892962', '1088', '1', 'deepseek-v4-flash', '327', '1415', '1');
INSERT INTO `token_usages` VALUES ('42', '11.9635', '11.9645', '80', '0.0010', '2026-06-19 16:45:29.032098', '75', '1', 'deepseek-v4-flash', '347', '422', '1');
INSERT INTO `token_usages` VALUES ('43', '11.9632', '11.9635', '81', '0.0003', '2026-06-19 23:46:19.323508', '68', '1', 'deepseek-v4-flash', '79', '147', '1');
INSERT INTO `token_usages` VALUES ('44', '11.9627', '11.9632', '81', '0.0005', '2026-06-20 13:47:18.675488', '54', '1', 'deepseek-v4-flash', '177', '231', '1');
INSERT INTO `token_usages` VALUES ('45', '11.9624', '11.9627', '82', '0.0003', '2026-06-20 13:48:06.594758', '28', '1', 'deepseek-v4-flash', '99', '127', '1');
INSERT INTO `token_usages` VALUES ('46', '11.9620', '11.9624', '83', '0.0004', '2026-06-20 13:48:30.946579', '38', '1', 'deepseek-v4-flash', '153', '191', '1');
INSERT INTO `token_usages` VALUES ('47', '11.9603', '11.9620', '84', '0.0017', '2026-06-20 17:44:55.811951', '936', '1', 'deepseek-v4-flash', '121', '1057', '1');
INSERT INTO `token_usages` VALUES ('48', '11.9586', '11.9603', '84', '0.0017', '2026-06-20 17:46:10.732766', '596', '1', 'deepseek-v4-flash', '320', '916', '1');
INSERT INTO `token_usages` VALUES ('49', '9.9988', '10.0000', '85', '0.0012', '2026-06-20 18:02:24.422801', '452', '1', 'deepseek-v4-flash', '199', '651', '4');
INSERT INTO `token_usages` VALUES ('50', '9.9984', '9.9988', '85', '0.0004', '2026-06-20 18:03:00.266545', '163', '1', 'deepseek-v4-flash', '81', '244', '4');
INSERT INTO `token_usages` VALUES ('51', '9.9976', '9.9984', '85', '0.0008', '2026-06-20 18:03:32.325050', '233', '1', 'deepseek-v4-flash', '175', '408', '4');
INSERT INTO `token_usages` VALUES ('52', '9.9971', '9.9976', '86', '0.0005', '2026-06-20 18:04:23.880941', '93', '1', 'deepseek-v4-flash', '133', '226', '4');
INSERT INTO `token_usages` VALUES ('53', '9.9966', '9.9971', '86', '0.0005', '2026-06-20 18:04:42.802763', '37', '1', 'deepseek-v4-flash', '170', '207', '4');
INSERT INTO `token_usages` VALUES ('54', '9.9961', '9.9966', '87', '0.0005', '2026-06-20 18:05:05.666246', '40', '1', 'deepseek-v4-flash', '169', '209', '4');
INSERT INTO `token_usages` VALUES ('55', '11.9583', '11.9586', '90', '0.0003', '2026-06-21 14:11:23.383797', '5', '1', 'deepseek-v4-flash', '136', '141', '1');
INSERT INTO `token_usages` VALUES ('56', '11.9578', '11.9583', '91', '0.0005', '2026-06-26 23:46:26.764554', '5', '1', 'deepseek-v4-flash', '181', '186', '1');
INSERT INTO `token_usages` VALUES ('57', '11.9575', '11.9578', '92', '0.0003', '2026-06-27 01:00:11.678457', '5', '1', 'deepseek-v4-flash', '123', '128', '1');
INSERT INTO `token_usages` VALUES ('58', '11.9573', '11.9575', '93', '0.0002', '2026-06-28 14:09:06.920576', '5', '1', 'deepseek-v4-flash', '69', '74', '1');
INSERT INTO `token_usages` VALUES ('59', '11.9573', '11.9573', '93', '0.0000', '2026-06-28 14:09:59.377470', '1', '6', 'gemini-3-flash-preview', '1', '2', '1');
INSERT INTO `token_usages` VALUES ('60', '11.9568', '11.9573', '93', '0.0005', '2026-06-28 14:11:39.153772', '17', '1', 'deepseek-v4-flash', '174', '191', '1');
INSERT INTO `token_usages` VALUES ('61', '11.9565', '11.9568', '94', '0.0003', '2026-06-28 14:12:00.018012', '5', '1', 'deepseek-v4-flash', '116', '121', '1');
INSERT INTO `token_usages` VALUES ('62', '11.9558', '11.9565', '94', '0.0007', '2026-06-28 14:12:08.041192', '67', '1', 'deepseek-v4-flash', '246', '313', '1');
INSERT INTO `token_usages` VALUES ('63', '11.9555', '11.9558', '94', '0.0003', '2026-06-28 14:12:31.745841', '3', '6', 'gemini-3-flash-preview', '57', '60', '1');
INSERT INTO `token_usages` VALUES ('64', '11.9550', '11.9555', '96', '0.0005', '2026-06-28 14:18:19.636830', '5', '1', 'deepseek-v4-flash', '196', '201', '1');
INSERT INTO `token_usages` VALUES ('65', '11.9543', '11.9550', '96', '0.0007', '2026-06-28 14:18:33.939880', '61', '1', 'deepseek-v4-flash', '234', '295', '1');
INSERT INTO `token_usages` VALUES ('66', '11.9537', '11.9543', '96', '0.0006', '2026-06-28 14:22:04.995776', '130', '1', 'deepseek-v4-flash', '144', '274', '1');
INSERT INTO `token_usages` VALUES ('67', '11.9529', '11.9537', '96', '0.0008', '2026-06-28 14:23:38.390777', '202', '1', 'deepseek-v4-flash', '198', '400', '1');
INSERT INTO `token_usages` VALUES ('68', '11.9523', '11.9529', '96', '0.0006', '2026-06-28 14:25:12.521838', '248', '1', 'deepseek-v4-flash', '100', '348', '1');
INSERT INTO `token_usages` VALUES ('69', '11.9513', '11.9523', '96', '0.0010', '2026-06-28 14:25:30.933774', '328', '1', 'deepseek-v4-flash', '209', '537', '1');
INSERT INTO `token_usages` VALUES ('70', '11.9502', '11.9513', '96', '0.0011', '2026-06-28 21:17:58.756211', '492', '1', 'deepseek-v4-flash', '144', '636', '1');
INSERT INTO `token_usages` VALUES ('71', '11.9498', '11.9502', '101', '0.0004', '2026-06-28 21:46:27.768103', '7', '1', 'deepseek-v4-flash', '143', '150', '1');
INSERT INTO `token_usages` VALUES ('72', '11.9474', '11.9498', '102', '0.0024', '2026-06-28 21:52:02.996383', '1088', '1', 'deepseek-v4-flash', '302', '1390', '1');
INSERT INTO `token_usages` VALUES ('73', '11.9466', '11.9474', '102', '0.0008', '2026-07-08 22:58:00.336803', '248', '1', 'deepseek-v4-flash', '176', '424', '1');
INSERT INTO `token_usages` VALUES ('74', '11.9453', '11.9466', '102', '0.0013', '2026-07-15 23:19:00.149461', '283', '1', 'deepseek-v4-flash', '351', '634', '1');
INSERT INTO `token_usages` VALUES ('75', '11.9423', '11.9453', '103', '0.0030', '2026-07-15 23:19:21.147796', '1356', '1', 'deepseek-v4-flash', '389', '1745', '1');
INSERT INTO `token_usages` VALUES ('76', '11.9404', '11.9423', '103', '0.0019', '2026-07-15 23:20:03.474963', '720', '1', 'deepseek-v4-flash', '347', '1067', '1');

-- ----------------------------
-- Table structure for usage_history
-- ----------------------------
DROP TABLE IF EXISTS `usage_history`;
CREATE TABLE `usage_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `prompt_id` bigint NOT NULL COMMENT '提示词ID',
  `action` varchar(20) NOT NULL COMMENT '操作类型: save/copy/use',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`created_at`),
  KEY `prompt_id` (`prompt_id`),
  CONSTRAINT `usage_history_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `usage_history_ibfk_2` FOREIGN KEY (`prompt_id`) REFERENCES `prompts_hub` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of usage_history
-- ----------------------------

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
  `reserved_balance` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `enabled` bit(1) NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint DEFAULT NULL,
  `signature` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_checkin_date` date DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES ('1', '$2a$10$dSE.YcZg/8AyGGkoJeyQvuquv8cUT4FnytJWyrHgRYlG8denlsUXS', 'testuser', '000001', '0', '1405921723@qq.com', null, '12.1904', '0.0000', '', 'USER', '2026-06-11 16:18:32', '138', 'CIallo 1111', '/uploads/userPic/avatar_1_45d0c4f1.png', '2026-07-16');
INSERT INTO `users` VALUES ('4', '$2a$10$n4Q.V0DbbufVTyTrJh6TbOI5tyFecU1YLCJ5Uvm4ELmLAxVbHdcJ2', 'admin', '999999', '0', 'admin@aichat.com', null, '9.9961', '0.0000', '', 'ADMIN', '2026-06-11 16:22:12', '18', 'nihao', '/uploads/userPic/avatar_4_9ba41523.jpg', null);

-- ----------------------------
-- Table structure for user_likes
-- ----------------------------
DROP TABLE IF EXISTS `user_likes`;
CREATE TABLE `user_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `target_id` bigint NOT NULL,
  `target_type` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_type`,`target_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of user_likes
-- ----------------------------
INSERT INTO `user_likes` VALUES ('1', '2026-06-14 21:37:46.264208', '1', 'PROMPT', '1');
INSERT INTO `user_likes` VALUES ('2', '2026-06-14 21:37:53.098762', '1', 'COMMENT', '1');
INSERT INTO `user_likes` VALUES ('3', '2026-06-14 22:34:36.657934', '1', 'PROMPT', '4');
INSERT INTO `user_likes` VALUES ('4', '2026-06-14 22:34:40.190565', '5', 'COMMENT', '4');
INSERT INTO `user_likes` VALUES ('5', '2026-06-14 22:34:40.989929', '3', 'COMMENT', '4');
INSERT INTO `user_likes` VALUES ('6', '2026-06-14 22:34:41.573683', '4', 'COMMENT', '4');
INSERT INTO `user_likes` VALUES ('7', '2026-06-14 22:49:35.040802', '5', 'PROMPT', '1');
INSERT INTO `user_likes` VALUES ('8', '2026-07-09 15:34:34.414148', '19', 'PROMPT', '1');
