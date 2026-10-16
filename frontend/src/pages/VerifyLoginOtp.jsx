import { Navigate, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import OtpForm from '../components/OtpForm.jsx';
import { authService } from '../services/authService';
import { useAuth } from '../hooks/useAuth';
import { pendingFlow } from '../utils/pendingFlow';
import { toastSuccess } from '../utils/alerts';

export default function VerifyLoginOtp() {
  const navigate = useNavigate();
  const { completeAuth } = useAuth();
  const flow = pendingFlow.get();

  if (!flow?.email || flow.purpose !== 'LOGIN') return <Navigate to="/login" replace />;

  const handleVerify = async (otp) => {
    const res = await authService.verifyLoginOtp(flow.email, otp);
    pendingFlow.clear();
    completeAuth(res.data);
    toastSuccess('Logged in successfully');
    navigate('/home', { replace: true });
  };

  return (
    <AuthLayout title="Check your email" subtitle="Enter the login code we just sent you.">
      <OtpForm
        email={flow.email}
        purpose="LOGIN"
        onVerify={handleVerify}
        submitLabel="Verify and log in"
        backTo="/login"
        backLabel="Back to login"
      />
    </AuthLayout>
  );
}
