# --- !Ups

create table "people" (
  "id" bigint not null AUTO_INCREMENT,
  "name" varchar(255) not null,
  "age" int not null,
  "gender" varchar(255) not null
);

# --- !Downs

drop table "people";
