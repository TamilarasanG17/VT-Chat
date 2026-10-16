import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout.jsx';
import ChatsLayout from '../layouts/ChatsLayout.jsx';
import ProtectedRoute from './ProtectedRoute.jsx';
import PublicRoute from './PublicRoute.jsx';
import Register from '../pages/Register.jsx';
import VerifyOtp from '../pages/VerifyOtp.jsx';
import Login from '../pages/Login.jsx';
import VerifyLoginOtp from '../pages/VerifyLoginOtp.jsx';
import ForgotPassword from '../pages/ForgotPassword.jsx';
import VerifyResetOtp from '../pages/VerifyResetOtp.jsx';
import ResetPassword from '../pages/ResetPassword.jsx';
import Home from '../pages/Home.jsx';
import Chats from '../pages/Chats.jsx';
import Calls from '../pages/Calls.jsx';
import AIChat from '../pages/AIChat.jsx';
import Chat from '../pages/Chat.jsx';
import Profile from '../pages/Profile.jsx';
import Notifications from '../pages/Notifications.jsx';
import Settings from '../pages/Settings.jsx';
import NotFound from '../pages/NotFound.jsx';

export default function AppRoutes() {
  return (
    <Routes>
        <Route element={<PublicRoute />}>
          <Route path="/register" element={<Register />} />
          <Route path="/verify-otp" element={<VerifyOtp />} />
          <Route path="/login" element={<Login />} />
          <Route path="/verify-login-otp" element={<VerifyLoginOtp />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/verify-reset-otp" element={<VerifyResetOtp />} />
          <Route path="/reset-password" element={<ResetPassword />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/home" element={<Home />} />
            <Route element={<ChatsLayout />}>
              <Route path="/chats" element={<Chats />} />
              <Route path="/chat/:conversationId" element={<Chat />} />
            </Route>
            <Route path="/calls" element={<Calls />} />
            <Route path="/ai" element={<AIChat />} />
            <Route path="/profile" element={<Profile />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route path="/settings" element={<Settings />} />
          </Route>
        </Route>

        <Route path="/" element={<Navigate to="/home" replace />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
  );
}
