-- Insert seed users (mix of Viewers/Students and Operators/IT Staff)
INSERT INTO users (id, email, full_name, role) VALUES
(1, 'student.alex@campus.edu', 'Alex Miller', 'ROLE_VIEWER'),
(2, 'student.sarah@campus.edu', 'Sarah Jenkins', 'ROLE_VIEWER'),
(3, 'tech.dave@campus.edu', 'Dave Reynolds', 'ROLE_OPERATOR'),
(4, 'admin.elena@campus.edu', 'Elena Vance', 'ROLE_OPERATOR');

-- Reset sequence for users if necessary
SELECT setval('users_id_seq', 4, true);

-- Insert seed incidents representing common campus IT issues
INSERT INTO incidents (id, title, description, status, severity, reporter_id, assigned_to) VALUES
(1, 'Library 3rd Floor Wi-Fi Dropping', 'Wireless connections keep cycling and disconnecting every 5 minutes near the printing kiosks.', 'IN_PROGRESS', 'HIGH', 1, 3),
(2, 'Canvas LMS Login Redirect Error', 'Receiving a 403 authorization error when attempting to log into Canvas via single sign-on.', 'OPEN', 'CRITICAL', 2, NULL),
(3, 'Student Union Printer Jam', 'Printer #4 is displaying a persistent paper jam error despite tray clearance.', 'RESOLVED', 'MEDIUM', 1, 3),
(4, 'Science Lab Lecture Hall Projector No Signal', 'HDMI input on the front projector is not detecting laptops in Hall 102.', 'OPEN', 'HIGH', 2, 4);

-- Reset sequence for incidents
SELECT setval('incidents_id_seq', 4, true);

-- Insert seed audit logs matching requirement F6 (Append-only audit trail)
INSERT INTO audit_logs (actor_email, action, resource_type, resource_id, before_state_json, after_state_json) VALUES
('student.alex@campus.edu', 'CREATE', 'INCIDENT', 1, NULL, '{"title":"Library 3rd Floor Wi-Fi Dropping","status":"OPEN","severity":"HIGH"}'),
('tech.dave@campus.edu', 'UPDATE', 'INCIDENT', 1, '{"status":"OPEN"}', '{"status":"IN_PROGRESS","assigned_to":"tech.dave@campus.edu"}'),
('student.alex@campus.edu', 'CREATE', 'INCIDENT', 3, NULL, '{"title":"Student Union Printer Jam","status":"OPEN","severity":"MEDIUM"}'),
('tech.dave@campus.edu', 'UPDATE', 'INCIDENT', 3, '{"status":"IN_PROGRESS"}', '{"status":"RESOLVED"}');