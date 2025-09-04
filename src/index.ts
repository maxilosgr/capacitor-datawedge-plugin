import { registerPlugin } from '@capacitor/core';

import type { DataWedgePlugin } from './definitions';

const DataWedge = registerPlugin<DataWedgePlugin>('DataWedge');

export * from './definitions';
export { DataWedge };