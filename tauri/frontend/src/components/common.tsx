import { useRef, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldLabel, FieldDescription } from "@/components/ui/field";
import {
  AlertDialog,
  AlertDialogTrigger,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from "@/components/ui/alert-dialog";
import { LoaderCircleIcon } from "lucide-react";
import { errorText } from "@/lib/native";
export function Panel({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}
export function Message({ children }: { children: ReactNode }) {
  return children ? (
    <Alert className="mt-4">
      <AlertDescription role="status">{children}</AlertDescription>
    </Alert>
  ) : null;
}
export function Action({
  children,
  action,
  disabled = false,
  variant = "outline",
  done,
}: {
  children: ReactNode;
  action: () => Promise<unknown>;
  disabled?: boolean;
  variant?: "default" | "outline" | "ghost" | "destructive";
  done?: () => void;
}) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const guard = useRef(false);
  return (
    <span className="inline-flex max-w-full flex-col gap-2">
      <Button
        variant={variant}
        disabled={disabled || busy}
        onClick={async () => {
          if (guard.current) return;
          guard.current = true;
          setBusy(true);
          setError("");
          try {
            await action();
            done?.();
          } catch (e) {
            setError(errorText(e));
          } finally {
            guard.current = false;
            setBusy(false);
          }
        }}
      >
        {busy && (
          <LoaderCircleIcon className="animate-spin" data-icon="inline-start" />
        )}
        {children}
      </Button>
      {error && (
        <span role="alert" className="text-sm text-destructive">
          {error}
        </span>
      )}
    </span>
  );
}
export function CheckField({
  id,
  label,
  description,
  checked,
  disabled,
  onChange,
}: {
  id: string;
  label: string;
  description?: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <Field
      orientation="horizontal"
      className="choice-row"
      data-disabled={disabled}
    >
      <Checkbox
        id={id}
        checked={checked}
        disabled={disabled}
        onCheckedChange={(value) => onChange(value === true)}
      />
      <div className="flex flex-col gap-1">
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {description && <FieldDescription>{description}</FieldDescription>}
      </div>
    </Field>
  );
}
export function Confirm({
  label,
  title,
  description,
  action,
}: {
  label: string;
  title: string;
  description: string;
  action: () => Promise<unknown>;
}) {
  const [open, setOpen] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const guard = useRef(false);
  return (
    <AlertDialog
      open={open}
      onOpenChange={(value) => {
        if (!guard.current) {
          setOpen(value);
          setError("");
        }
      }}
    >
      <AlertDialogTrigger asChild>
        <Button variant="outline">{label}</Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={busy}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={busy}
            onClick={async (event) => {
              event.preventDefault();
              if (guard.current) return;
              guard.current = true;
              setBusy(true);
              setError("");
              try {
                await action();
                setOpen(false);
              } catch (e) {
                setError(errorText(e));
              } finally {
                guard.current = false;
                setBusy(false);
              }
            }}
          >
            {busy ? "Working…" : label}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
