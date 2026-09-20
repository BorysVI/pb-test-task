DROP INDEX idx_orders_stuck;

CREATE INDEX idx_orders_unsettled ON orders (status, updated_at) WHERE status IN ('NEW', 'PROCESSING');
