import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import HomePage from './pages/HomePage';
import UploadPage from './pages/UploadPage';
import RunLogsDashboard from './pages/telemetry/RunLogsDashboard';
import RunDetailView from './pages/telemetry/RunDetailView';
import DlqMonitor from './pages/telemetry/DlqMonitor';
import EventStreamMonitor from './pages/telemetry/EventStreamMonitor';
import './App.css';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <HomePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/upload"
            element={
              <ProtectedRoute>
                <UploadPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/telemetry"
            element={
              <ProtectedRoute>
                <RunLogsDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/telemetry/runs/:runId"
            element={
              <ProtectedRoute>
                <RunDetailView />
              </ProtectedRoute>
            }
          />
          <Route
            path="/telemetry/dlq"
            element={
              <ProtectedRoute>
                <DlqMonitor />
              </ProtectedRoute>
            }
          />
          <Route
            path="/telemetry/events"
            element={
              <ProtectedRoute>
                <EventStreamMonitor />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
