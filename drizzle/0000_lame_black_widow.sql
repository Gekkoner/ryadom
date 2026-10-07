CREATE TABLE `chats` (
	`id` text PRIMARY KEY NOT NULL,
	`kind` text NOT NULL,
	`title` text DEFAULT '' NOT NULL,
	`pair` text,
	`created` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `chats_pair_unique` ON `chats` (`pair`);--> statement-breakpoint
CREATE TABLE `files` (
	`id` text PRIMARY KEY NOT NULL,
	`owner` text NOT NULL,
	`chat_id` text,
	`name` text NOT NULL,
	`mime` text NOT NULL,
	`size` integer NOT NULL,
	`created` integer NOT NULL,
	FOREIGN KEY (`owner`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`chat_id`) REFERENCES `chats`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `files_owner` ON `files` (`owner`);--> statement-breakpoint
CREATE TABLE `limits` (
	`id` text PRIMARY KEY NOT NULL,
	`count` integer NOT NULL,
	`expires` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `members` (
	`id` text PRIMARY KEY NOT NULL,
	`chat_id` text NOT NULL,
	`user_id` text NOT NULL,
	FOREIGN KEY (`chat_id`) REFERENCES `chats`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE UNIQUE INDEX `members_pair` ON `members` (`chat_id`,`user_id`);--> statement-breakpoint
CREATE INDEX `members_user` ON `members` (`user_id`);--> statement-breakpoint
CREATE TABLE `messages` (
	`id` text PRIMARY KEY NOT NULL,
	`chat_id` text NOT NULL,
	`sender` text NOT NULL,
	`body` text DEFAULT '' NOT NULL,
	`file_id` text,
	`kind` text DEFAULT 'text' NOT NULL,
	`created` integer NOT NULL,
	`nonce` text NOT NULL,
	FOREIGN KEY (`chat_id`) REFERENCES `chats`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`sender`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `messages_chat_time` ON `messages` (`chat_id`,`created`);--> statement-breakpoint
CREATE UNIQUE INDEX `messages_nonce` ON `messages` (`sender`,`nonce`);--> statement-breakpoint
CREATE TABLE `sessions` (
	`token` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`expires` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `sessions_user` ON `sessions` (`user_id`);--> statement-breakpoint
CREATE TABLE `users` (
	`id` text PRIMARY KEY NOT NULL,
	`handle` text NOT NULL,
	`name` text NOT NULL,
	`bio` text DEFAULT '' NOT NULL,
	`avatar` text,
	`accent` text DEFAULT '#d1fa70' NOT NULL,
	`salt` text NOT NULL,
	`password` text NOT NULL,
	`recovery` text NOT NULL,
	`created` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `users_handle_unique` ON `users` (`handle`);