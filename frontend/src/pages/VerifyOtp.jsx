import { Navigate, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import OtpForm from '../components/OtpForm.jsx';
import { authService } from '../services/authService';
import { useAuth } from '../hooks/useAuth';
import { pendingFlow } from '../utils/pendingFlow';
import { toastSuccess } from '../utils/alerts';

export default function VerifyOtp() {
  const navigate = useNavigate();
  const { completeAuth } = useAuth();
  const flow = pendingFlow.get();

  if (!flow?.email || flow.purpose !== 'REGISTER') return <Navigate to="/register" replace />;

  const handleVerify = async (otp) => {
    const res = await authService.verifyRegisterOtp(flow.email, otp);
    pendingFlow.clear();
    completeAuth(res.data);
    toastSuccess('Email verified. Welcome to VTChat!');
    navigate('/home', { replace: true });
  };

  return (
    <AuthLayout title="Verify your email" subtitle="Enter the code we emailed you to finish creating your account.">
      <OtpForm
        email={flow.email}
        purpose="REGISTER"
        onVerify={handleVerify}
        submitLabel="Verify email"
        backTo="/register"
        backLabel="Use a different email"
      />
    </AuthLayout>
  );
}
