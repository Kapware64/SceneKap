# --- !Ups

create table "places" (
  "id" bigint not null AUTO_INCREMENT,
  "pid" varchar(255) not null,
  "rComments" varchar(2040) not null,
  "tComments" varchar(1275) not null,
  "website" varchar(255) not null,
  "photo_uri" varchar(510) not null,
  "summary" varchar(4096) not null,
  "last_summary_mod" varchar(255) not null,
  "extra" varchar(255) not null
);

# --- !Downs

drop table "people";
