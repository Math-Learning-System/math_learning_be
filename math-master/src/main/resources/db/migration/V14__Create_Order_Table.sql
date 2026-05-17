-- ============================================================================
-- Migration: Create Orders Table
-- Description: Creates the orders table for the course purchase billing flow
-- Author: System
-- Date: 2026-04-23
-- ============================================================================

-- ─── Orders Table ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL,
    course_id UUID NOT NULL,
    enrollment_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_number VARCHAR(50) NOT NULL UNIQUE,
    original_price DECIMAL(15, 2),
    discount_amount DECIMAL(15, 2) DEFAULT 0,
    final_price DECIMAL(15, 2) NOT NULL,
    instructor_earnings DECIMAL(15, 2),
    platform_commission DECIMAL(15, 2),
    expires_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT fk_orders_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_orders_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_orders_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE SET NULL,
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_orders_final_price CHECK (final_price >= 0)
);

-- Indexes for orders table
CREATE INDEX IF NOT EXISTS idx_orders_student ON orders(student_id);
CREATE INDEX IF NOT EXISTS idx_orders_course ON orders(course_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_number ON orders(order_number);
CREATE INDEX IF NOT EXISTS idx_orders_expires_at ON orders(expires_at);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);

-- ─── Update Transactions Table ───────────────────────────────────────────

-- Add order_id column to transactions table if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'transactions' AND column_name = 'order_id'
    ) THEN
        ALTER TABLE transactions ADD COLUMN order_id UUID;
        ALTER TABLE transactions ADD CONSTRAINT fk_transactions_order
            FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;
        CREATE INDEX IF NOT EXISTS idx_transactions_order ON transactions(order_id);
    END IF;
END $$;

-- ─── Comments ─────────────────────────────────────────────────────────────

COMMENT ON TABLE orders IS 'Stores course purchase orders with confirmation workflow';
COMMENT ON COLUMN orders.status IS 'Order status: PENDING (awaiting confirmation), PROCESSING (payment in progress), COMPLETED (successful), FAILED (payment failed), CANCELLED (user cancelled or expired)';
COMMENT ON COLUMN orders.expires_at IS 'Order expiry time (15 minutes from creation)';
COMMENT ON COLUMN orders.instructor_earnings IS '90% of final price goes to instructor';
COMMENT ON COLUMN orders.platform_commission IS '10% of final price goes to platform';
