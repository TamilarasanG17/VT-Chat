import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext.jsx';
import { AuthProvider } from './context/AuthContext.jsx';
import { PresenceProvider } from './context/PresenceContext.jsx';
import { NotificationProvider } from './context/NotificationContext.jsx';
import { CallProvider } from './context/CallContext.jsx';
import CallOverlay from './components/call/CallOverlay.jsx';
import AppRoutes from './routes/AppRoutes.jsx';

export default function App() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <AuthProvider>
          <PresenceProvider>
            <NotificationProvider>
              <CallProvider>
                <AppRoutes />
                <CallOverlay />
              </CallProvider>
            </NotificationProvider>
          </PresenceProvider>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  );
}
