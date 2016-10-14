# --- !Ups
alter table "users" add "password" varchar(255) not null;

# --- !Downs

alter table "users" drop "password";
