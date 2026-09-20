import { register } from '../core/registry.js';
import { RectElement } from './RectElement.js';
import { EllipseElement } from './EllipseElement.js';
import { TriangleElement } from './TriangleElement.js';
import { LineElement } from './LineElement.js';
import { TextElement } from './TextElement.js';
import { ImageElement } from './ImageElement.js';

register('rect', RectElement);
register('ellipse', EllipseElement);
register('triangle', TriangleElement);
register('line', LineElement);
register('text', TextElement);
register('image', ImageElement);

export { RectElement, EllipseElement, TriangleElement, LineElement, TextElement, ImageElement };
