'use client';

import { useState } from 'react';
import { motion } from 'framer-motion';
import { toast } from 'sonner';
import { Save, RotateCcw } from 'lucide-react';
import { useSystemConfig, useUpdateSystemConfig } from '@/hooks/use-admin';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Label } from '@/components/ui/label';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

export default function AdminConfigsPage() {
  const { data: configs, isLoading } = useSystemConfig();
  const updateMutation = useUpdateSystemConfig();
  const [editValues, setEditValues] = useState<Record<string, string>>({});

  const handleSave = async (key: string) => {
    const value = editValues[key];
    if (value === undefined) return;
    try {
      await updateMutation.mutateAsync({ key, data: { value } });
      toast.success('Config updated');
    } catch { toast.error('Failed to update config'); }
  };

  const handleEdit = (key: string, value: string) => {
    setEditValues((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item}>
        <h2 className="text-2xl font-bold tracking-tight">System Configuration</h2>
        <p className="text-muted-foreground">Manage global system settings</p>
      </motion.div>

      <motion.div variants={item}>
        <Card>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-6 space-y-4">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}</div>
            ) : !configs || configs.length === 0 ? (
              <div className="p-8 text-center text-muted-foreground">No config items found</div>
            ) : (
              <div className="divide-y">
                {configs.map((config, i) => {
                  const isEditing = editValues.hasOwnProperty(config.key);
                  const displayValue = isEditing ? editValues[config.key] : config.value;
                  return (
                    <motion.div key={config.key} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: i * 0.03 }} className="px-4 py-4">
                      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                        <div className="space-y-0.5">
                          <Label className="text-sm font-medium">{config.key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())}</Label>
                          {config.description && <p className="text-xs text-muted-foreground">{config.description}</p>}
                        </div>
                        <div className="flex items-center gap-2">
                          <Input
                            value={displayValue}
                            onChange={(e) => handleEdit(config.key, e.target.value)}
                            className="w-40 h-8 text-xs font-mono"
                            onFocus={() => { if (!isEditing) handleEdit(config.key, config.value); }}
                          />
                          {isEditing && (
                            <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} className="flex gap-1">
                              <Button
                                size="sm"
                                variant="ghost"
                                className="h-7 w-7 p-0"
                                onClick={() => { setEditValues((prev) => { const next = { ...prev }; delete next[config.key]; return next; }); }}
                              >
                                <RotateCcw className="h-3 w-3" />
                              </Button>
                              <Button
                                size="sm"
                                className="h-7 px-2 gap-1"
                                onClick={() => handleSave(config.key)}
                                disabled={updateMutation.isPending}
                              >
                                <Save className="h-3 w-3" />
                              </Button>
                            </motion.div>
                          )}
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}
