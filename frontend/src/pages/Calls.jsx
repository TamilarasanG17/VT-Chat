import CallHistoryList from '../components/call/CallHistoryList.jsx';
import Card from '../components/Card.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { callService } from '../services/callService';

export default function Calls() {
  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title="Calls" description="Your recent voice and video calls." />
      <Card className="overflow-hidden">
        <CallHistoryList loadPage={callService.getMyCalls} emptyText="No calls yet. Start one from a chat." />
      </Card>
    </div>
  );
}
