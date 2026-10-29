-- Money is stored as BIGINT minor units (cents, paise). Never floats.

create table users (
    id            uuid primary key,
    email         varchar(320) not null unique,
    password_hash varchar(100) not null,
    full_name     varchar(200) not null,
    created_at    timestamptz  not null
);

-- An account is a holder of funds (a person, a business, or the platform itself).
create table accounts (
    id         uuid primary key,
    owner_id   uuid references users (id),
    name       varchar(200) not null,
    type       varchar(20)  not null check (type in ('PERSONAL', 'BUSINESS', 'SYSTEM')),
    created_at timestamptz  not null,
    constraint accounts_owner_required check (type = 'SYSTEM' or owner_id is not null)
);
create index accounts_owner_idx on accounts (owner_id);

-- A wallet holds one currency for one account. `balance` is a cached projection of the
-- ledger, updated in the same transaction as the entries that change it.
create table wallets (
    id             uuid primary key,
    account_id     uuid        not null references accounts (id),
    currency       varchar(3)  not null check (currency ~ '^[A-Z]{3}$'),
    balance        bigint      not null default 0,
    status         varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE', 'FROZEN')),
    allow_negative boolean     not null default false,
    version        bigint      not null default 0,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    constraint wallets_balance_non_negative check (allow_negative or balance >= 0),
    constraint wallets_one_per_currency unique (account_id, currency)
);

create table ledger_transactions (
    id           uuid primary key,
    type         varchar(20)  not null check (type in ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL')),
    amount       bigint       not null check (amount > 0),
    currency     varchar(3)      not null,
    description  varchar(500),
    initiated_by uuid references users (id),
    created_at   timestamptz  not null
);

-- DEBIT decreases a wallet's balance, CREDIT increases it.
-- Every ledger transaction must have credits == debits (enforced below at commit time).
create table ledger_entries (
    id             uuid primary key,
    seq            bigserial   not null unique,
    transaction_id uuid        not null references ledger_transactions (id),
    wallet_id      uuid        not null references wallets (id),
    direction      varchar(6)  not null check (direction in ('DEBIT', 'CREDIT')),
    amount         bigint      not null check (amount > 0),
    currency       varchar(3)  not null check (currency ~ '^[A-Z]{3}$'),
    balance_after  bigint      not null,
    created_at     timestamptz not null
);
create index ledger_entries_wallet_seq_idx on ledger_entries (wallet_id, seq desc);
create index ledger_entries_transaction_idx on ledger_entries (transaction_id);

create function ledger_check_transaction_balanced() returns trigger as
$$
declare
    imbalance bigint;
begin
    select coalesce(sum(case when direction = 'CREDIT' then amount else -amount end), 0)
    into imbalance
    from ledger_entries
    where transaction_id = new.transaction_id;

    if imbalance <> 0 then
        raise exception 'ledger transaction % is unbalanced by %', new.transaction_id, imbalance;
    end if;
    return null;
end;
$$ language plpgsql;

-- Deferred so the check runs at COMMIT, after both legs of the transaction are inserted.
create constraint trigger ledger_entries_balanced
    after insert on ledger_entries
    deferrable initially deferred
    for each row execute function ledger_check_transaction_balanced();

create function forbid_mutation() returns trigger as
$$
begin
    raise exception '% is append-only', tg_table_name;
end;
$$ language plpgsql;

create trigger ledger_transactions_append_only
    before update or delete on ledger_transactions
    for each row execute function forbid_mutation();

create trigger ledger_entries_append_only
    before update or delete on ledger_entries
    for each row execute function forbid_mutation();

create table idempotency_keys (
    user_id         uuid         not null references users (id),
    idempotency_key varchar(255) not null,
    request_hash    char(64)     not null,
    response_status int,
    response_body   text,
    created_at      timestamptz  not null default now(),
    primary key (user_id, idempotency_key)
);
create index idempotency_keys_created_idx on idempotency_keys (created_at);

create table webhook_endpoints (
    id          uuid primary key,
    owner_id    uuid          not null references users (id),
    url         varchar(2048) not null,
    secret      varchar(100)  not null,
    event_types text[]        not null,
    active      boolean       not null default true,
    created_at  timestamptz   not null
);
create index webhook_endpoints_owner_idx on webhook_endpoints (owner_id);

-- Transactional outbox: rows are inserted in the same DB transaction as the money movement,
-- then delivered asynchronously with retries.
create table webhook_deliveries (
    id               uuid primary key,
    endpoint_id      uuid        not null references webhook_endpoints (id) on delete cascade,
    event_id         uuid        not null,
    event_type       varchar(50) not null,
    payload          text        not null,
    status           varchar(20) not null check (status in ('PENDING', 'DELIVERED', 'DEAD')),
    attempts         int         not null default 0,
    next_attempt_at  timestamptz not null,
    last_status_code int,
    last_error       varchar(1000),
    delivered_at     timestamptz,
    created_at       timestamptz not null
);
create index webhook_deliveries_due_idx on webhook_deliveries (next_attempt_at) where status = 'PENDING';
create index webhook_deliveries_endpoint_idx on webhook_deliveries (endpoint_id, created_at desc);

create table audit_logs (
    id          uuid primary key,
    actor_id    uuid references users (id),
    action      varchar(50) not null,
    entity_type varchar(50) not null,
    entity_id   uuid,
    details     jsonb,
    ip_address  varchar(45),
    created_at  timestamptz not null
);
create index audit_logs_actor_idx on audit_logs (actor_id, created_at desc);

create trigger audit_logs_append_only
    before update or delete on audit_logs
    for each row execute function forbid_mutation();

-- The platform's own account. Its wallets are settlement wallets that mirror money entering
-- and leaving the system, so they are allowed to go negative.
insert into accounts (id, owner_id, name, type, created_at)
values ('00000000-0000-0000-0000-000000000001', null, 'Platform settlement', 'SYSTEM', now());
