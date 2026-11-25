import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';

export default class AsyncControlsBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unable to load incident controls', error, info);
  }

  render() {
    if (this.state.failed) {
      return <p role="alert">Unable to load incident controls. Refresh the page to try again.</p>;
    }

    return this.props.children;
  }
}
