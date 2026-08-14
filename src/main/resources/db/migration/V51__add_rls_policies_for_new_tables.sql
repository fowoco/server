-- Enable RLS on user_login_event table
ALTER TABLE user_login_event ENABLE ROW LEVEL SECURITY;

-- Policy for user_login_event: users can only see their own login events within their company
CREATE POLICY user_login_event_company_isolation ON user_login_event
    FOR ALL
    USING (company_id = current_setting('app.current_company_id')::UUID)
    WITH CHECK (company_id = current_setting('app.current_company_id')::UUID);

-- Enable RLS on notification_preference table
ALTER TABLE notification_preference ENABLE ROW LEVEL SECURITY;

-- Policy for notification_preference: users can only see their own preferences within their company
CREATE POLICY notification_preference_company_isolation ON notification_preference
    FOR ALL
    USING (company_id = current_setting('app.current_company_id')::UUID)
    WITH CHECK (company_id = current_setting('app.current_company_id')::UUID);
