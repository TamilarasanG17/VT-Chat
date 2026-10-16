import { Navigate, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import OtpForm from '../components/OtpForm.jsx';
import { authService } from '../services/authService';
import { pendingFlow } from '../utils/pendingFlow';

export default function VerifyResetOtp() {
  const navigate = useNavigate();
  const flow = pendingFlow.get();

  if (!flow?.email || flow.purpose !== 'RESET_PASSWORD') return <Navigate to="/forgot-password" replace />;

  const handleVerify = async (otp) => {
    const res = await authService.verifyResetOtp(flow.email, otp);
    navigate('/reset-password', { replace: true, state: { resetToken: res.data.resetToken } });
  };

  return (
    <AuthLayout title="Enter reset code" subtitle="Enter the code we emailed you to reset your password.">
      <OtpForm
        email={flow.email}
        purpose="RESET_PASSWORD"
        onVerify={handleVerify}
        submitLabel="Verify code"
        backTo="/forgot-password"
        backLabel="Use a different email"
      />
    </AuthLayout>
  );
}
