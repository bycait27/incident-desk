import { useEffect, useState, useMemo } from 'react';
import type { Incident } from './types';
import { fetchIncidents } from './services/api';
import { AlertCircle, Search, Filter, RefreshCw, ShieldAlert, Clock } from 'lucide-react';

function getFetchErrorMessage(err: unknown): string {
  return err instanceof Error && err.message
    ? err.message
    : 'An unexpected error occurred while connecting to the backend.';
}

export default function App() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Search & Filter state (F1)
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchIncidents();
      setIncidents(data);
    } catch (err: unknown) {
      setError(getFetchErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    fetchIncidents()
      .then((data) => {
        if (active) {
          setIncidents(data);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(getFetchErrorMessage(err));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  // Filter logic (F1)
  const filteredIncidents = useMemo(() => {
    return incidents.filter((inc) => {
      const matchesSearch = 
        inc.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        inc.description.toLowerCase().includes(searchQuery.toLowerCase());
      
      const matchesStatus = statusFilter === 'ALL' || inc.status === statusFilter;
      const matchesSeverity = severityFilter === 'ALL' || inc.severity === severityFilter;

      return matchesSearch && matchesStatus && matchesSeverity;
    });
  }, [incidents, searchQuery, statusFilter, severityFilter]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans p-6 md:p-10">
      <div className="max-w-6xl mx-auto space-y-8">
        
        {/* Header */}
        <header className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800 pb-6">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-white flex items-center gap-3">
              <ShieldAlert className="text-indigo-400 w-8 h-8" />
              IncidentDesk
            </h1>
            <p className="text-slate-400 text-sm mt-1">Campus IT Service-Status & Incident Tracking Dashboard</p>
          </div>
          <button 
            onClick={loadData}
            className="self-start md:self-auto flex items-center gap-2 bg-slate-800 hover:bg-slate-700 text-slate-200 px-4 py-2 rounded-lg text-sm font-medium transition border border-slate-700"
          >
            <RefreshCw className="w-4 h-4" /> Refresh Data
          </button>
        </header>

        {/* Controls: Search & Filters (F1) */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 bg-slate-900 p-4 rounded-xl border border-slate-800">
          <div className="relative">
            <Search className="absolute left-3 top-3 w-4 h-4 text-slate-500" />
            <input 
              type="text"
              placeholder="Search incidents by title or description..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg pl-10 pr-4 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500 transition"
            />
          </div>

          <div className="flex items-center gap-2">
            <Filter className="w-4 h-4 text-slate-500 shrink-0" />
            <select 
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500 transition"
            >
              <option value="ALL">All Statuses</option>
              <option value="OPEN">Open</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="RESOLVED">Resolved</option>
              <option value="CLOSED">Closed</option>
            </select>
          </div>

          <div>
            <select 
              value={severityFilter}
              onChange={(e) => setSeverityFilter(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500 transition"
            >
              <option value="ALL">All Severities</option>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </div>
        </div>

        {/* State Handling (F2): Loading, Error, Empty, and Data */}
        {loading && (
          <div className="flex flex-col items-center justify-center py-20 gap-4">
            <div className="w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
            <p className="text-slate-400 text-sm">Loading active incidents...</p>
          </div>
        )}

        {error && !loading && (
          <div className="bg-red-950/40 border border-red-800/60 rounded-xl p-6 text-center space-y-3">
            <AlertCircle className="w-10 h-10 text-red-400 mx-auto" />
            <h3 className="text-lg font-semibold text-red-200">Failed to Load Incidents</h3>
            <p className="text-sm text-red-300 max-w-md mx-auto">{error}</p>
            <button 
              onClick={loadData}
              className="mt-2 bg-red-800 hover:bg-red-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition"
            >
              Retry Connection
            </button>
          </div>
        )}

        {!loading && !error && filteredIncidents.length === 0 && (
          <div className="bg-slate-900/50 border border-slate-800 rounded-xl p-12 text-center space-y-3">
            <Clock className="w-10 h-10 text-slate-500 mx-auto" />
            <h3 className="text-lg font-medium text-slate-300">No matching incidents found</h3>
            <p className="text-sm text-slate-500">Try adjusting your search query or filter criteria.</p>
          </div>
        )}

        {!loading && !error && filteredIncidents.length > 0 && (
          <div className="grid grid-cols-1 gap-4">
            {filteredIncidents.map((incident) => (
              <div 
                key={incident.id} 
                className="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition space-y-3"
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <span className="text-xs font-mono text-indigo-400">INC-{incident.id}</span>
                    <h2 className="text-lg font-semibold text-white mt-0.5">{incident.title}</h2>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-medium uppercase tracking-wide ${
                      incident.severity === 'CRITICAL' ? 'bg-red-950 text-red-400 border border-red-800' :
                      incident.severity === 'HIGH' ? 'bg-orange-950 text-orange-400 border border-orange-800' :
                      'bg-slate-800 text-slate-300 border border-slate-700'
                    }`}>
                      {incident.severity}
                    </span>
                    <span className={`px-2.5 py-1 rounded-full text-xs font-medium uppercase tracking-wide ${
                      incident.status === 'RESOLVED' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' :
                      incident.status === 'IN_PROGRESS' ? 'bg-blue-950 text-blue-400 border border-blue-800' :
                      'bg-amber-950 text-amber-400 border border-amber-800'
                    }`}>
                      {incident.status.replace('_', ' ')}
                    </span>
                  </div>
                </div>
                <p className="text-slate-300 text-sm leading-relaxed">{incident.description}</p>
                <div className="flex items-center justify-between text-xs text-slate-500 pt-2 border-t border-slate-800/80">
                  <span>Created: {new Date(incident.createdAt).toLocaleString()}</span>
                  <span>Last Updated: {new Date(incident.updatedAt).toLocaleString()}</span>
                </div>
              </div>
            ))}
          </div>
        )}

      </div>
    </div>
  );
}
