# --- !Ups
drop table "people";

create table "users" (
  "id" bigint not null AUTO_INCREMENT,
  "username" varchar(255) not null,
  "settings" varchar(255) not null,
  "lastlogged" varchar(255) not null
);

# --- !Downs

drop table "users";

create table "people" (
  "id" bigint not null AUTO_INCREMENT,
  "name" varchar(255) not null,
  "age" int not null,
  "gender" varchar(255) not null
);
